package com.iamtt.downloader;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import org.libtorrent4j.*;
import org.libtorrent4j.swig.torrent_flags_t;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** One foreground worker owns the native session; no native calls on the UI thread. */
public class DownloadService extends Service {
    private static final int NOTIFICATION=701;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean running, destroyed;
    private DownloadStore store;
    private PowerManager.WakeLock wake;
    private volatile SessionManager session;
    private final ConnectivityManager.NetworkCallback networkWatcher = new ConnectivityManager.NetworkCallback() {
        // Network callbacks run on another thread. The worker checks the network
        // every second; never call the native session concurrently with stop().
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { }
        @Override public void onLost(Network n) { }
    };
    public static void start(Context c) {
        c.startForegroundService(new Intent(c,DownloadService.class));
    }
    @Override public void onCreate() {
        super.onCreate(); store=DownloadStore.get(this);
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel("downloads","Video downloads",NotificationManager.IMPORTANCE_LOW));
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"IAMTTDownloader:transfer");
        getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(networkWatcher);
    }
    @Override public synchronized int onStartCommand(Intent intent,int flags,int startId) {
        if (intent != null && "PAUSE_ALL".equals(intent.getAction())) { store.pauseAll(); return START_NOT_STICKY; }
        if(Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION,notification("Preparing downloads"),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTIFICATION,notification("Preparing downloads"));
        if(!running) { running=true; worker.execute(this::runQueue); }
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    private Notification notification(String text) {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).putExtra("downloads",true),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause=PendingIntent.getService(this,1,new Intent(this,DownloadService.class).setAction("PAUSE_ALL"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,"downloads").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("IAMTT Downloader").setContentText(text).setContentIntent(open).setOngoing(true)
            .setOnlyAlertOnce(true).addAction(android.R.drawable.ic_media_pause,"Pause all",pause).build();
    }
    private boolean allowedNetwork() {
        ConnectivityManager manager=getSystemService(ConnectivityManager.class);
        NetworkCapabilities n=manager.getNetworkCapabilities(manager.getActiveNetwork());
        return n!=null && n.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            && (!store.wifiOnly() || n.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
    }
    private boolean active(DownloadStore.Item i) {
        return !destroyed && !i.state.equals("Paused") && !i.state.equals("Complete") && !i.state.equals("Error");
    }
    private void waitForNetwork(DownloadStore.Item item) throws InterruptedException {
        while(active(item) && !allowedNetwork()) {
            if(session!=null) session.pause();
            item.state="Waiting for network";item.speed=0;item.detail=store.wifiOnly()?"Connect to Wi-Fi to continue.":"Waiting for internet.";
            Thread.sleep(1000);
        }
        if(session!=null && active(item)) session.resume();
    }
    private void runQueue() {
        try {
            wake.acquire(6*60*60*1000L);
            while(!destroyed) {
                DownloadStore.Item next=null;
                synchronized(this) {
                    for(DownloadStore.Item i:store.all()) if(i.state.equals("Queued")) {next=i;break;}
                    if(next==null) break;
                }
                runOne(next);
            }
        } catch(Throwable e) {
            for(DownloadStore.Item i:store.all()) if(active(i)) {i.state="Error";i.detail="Download engine could not start. Reopen the app and retry.";}
        } finally {
            if(session!=null) {session.stop();session=null;}
            if(wake.isHeld()) wake.release();
            store.save();
            synchronized(this) {
                running=false;
                boolean queued=false;
                for(DownloadStore.Item i:store.all()) if(i.state.equals("Queued")) queued=true;
                if(queued && !destroyed) {running=true;worker.execute(this::runQueue);}
                else {stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
            }
        }
    }
    private void runOne(DownloadStore.Item item) {
        TorrentHandle handle=null;
        try {
            waitForNetwork(item); if(!active(item)) return;
            if(session==null) {
                session=new SessionManager();
                SettingsPack settings=new SettingsPack();
                settings.setEnableDht(true);settings.setEnableLsd(true);
                CrashReports.checkpoint(this,"starting session");
                session.start(new SessionParams(settings));
            }
            item.peers=0;item.seeds=0;item.state="Finding peers";
            item.detail=item.reportedSeeds>=0
                ?"Fetching torrent metadata · "+item.reportedSeeds+" seeders reported by the addon. Up to 90 seconds."
                :"Fetching torrent metadata · addon did not report seeders. Up to 90 seconds.";
            File root=new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),item.hash);
            if(!root.exists() && !root.mkdirs()) throw new IOException("Cannot create the download folder.");
            File metadata=new File(getFilesDir(),item.hash+".torrent");
            CrashReports.checkpoint(this,"fetching metadata");
            byte[] bytes=metadata.exists()?Files.readAllBytes(metadata.toPath()):session.fetchMagnet(item.magnet,90,getCacheDir());
            // fetchMagnet removes its temporary handle asynchronously. Do not
            // re-use a metadata-only handle paused by STOP_WHEN_READY.
            long removalDeadline=System.currentTimeMillis()+10000;
            TorrentHandle pending=session.find(new Sha1Hash(item.hash));
            while(active(item)&&pending!=null&&pending.isValid()&&System.currentTimeMillis()<removalDeadline){Thread.sleep(100);pending=session.find(new Sha1Hash(item.hash));}
            if(pending!=null&&pending.isValid())throw new IOException("Metadata handle is still closing. Tap Resume to retry.");
            if(!active(item)) return;
            if(bytes==null) {
                String hint=item.reportedSeeds>=0
                    ?" The addon reported "+item.reportedSeeds+" seeders, but none supplied metadata to this device."
                    :" The addon did not provide a usable seeder count.";
                throw new IOException("No reachable peer supplied torrent metadata within 90 seconds."+hint+" Choose a source with stronger swarm health.");
            }
            TorrentInfo info=new TorrentInfo(bytes);
            if(!info.isValid() || !info.infoHash().toHex().equalsIgnoreCase(item.hash)) throw new IOException("Torrent metadata does not match the selected source.");
            Files.write(metadata.toPath(),bytes);
            FileStorage files=info.files(); int count=files.numFiles();
            String[] paths=new String[count];long[] sizes=new long[count];
            for(int i=0;i<count;i++) {paths[i]=files.filePath(i);sizes[i]=files.fileSize(i);Protocol.safeFile(root,paths[i]);}
            int selected=Protocol.selectFile(item.requested,paths,sizes);
            File output=Protocol.safeFile(root,paths[selected]);
            if(root.getUsableSpace()<Math.max(0,sizes[selected]-item.done)+64*1024*1024L)
                throw new IOException("Not enough free space for this video.");
            item.path=output.getAbsolutePath();item.total=sizes[selected];
            Priority[] priorities=new Priority[count];Arrays.fill(priorities,Priority.IGNORE);priorities[selected]=Priority.DEFAULT;
            CrashReports.checkpoint(this,"adding video torrent");
            session.download(info,root,null,priorities,null,new torrent_flags_t());
            long deadline=System.currentTimeMillis()+15000;
            while(active(item) && (handle=session.find(info.infoHash()))==null && System.currentTimeMillis()<deadline) Thread.sleep(200);
            if(!active(item)) return;
            if(handle==null || !handle.isValid()) throw new IOException("The torrent engine could not add this source.");
            // Explicitly retain addon tracker hints after the metadata handoff.
            for(String tracker:Protocol.magnetTrackers(item.magnet))handle.addTracker(new AnnounceEntry(tracker));
            handle.unsetFlags(TorrentFlags.UPLOAD_MODE.or_(TorrentFlags.STOP_WHEN_READY));
            handle.resume();handle.forceReannounce();
            int tick=0;
            while(active(item)) {
                waitForNetwork(item); if(!active(item)) break;
                CrashReports.checkpoint(this,"reading torrent status");
                TorrentStatus status=handle.status(true);
                if(status.errorCode().isError()) throw new IOException("The torrent engine reported a file or network error. Check storage and retry.");
                item.done=status.totalWantedDone();item.speed=status.downloadRate();item.peers=status.numPeers();item.seeds=status.numSeeds();
                CrashReports.checkpoint(this,"transferring peers="+item.peers+" bytes="+item.done);
                item.state="Downloading";item.detail=item.peers==0?"Waiting for peers. Availability depends on the source.":"Downloading selected video";
                if(status.isFinished() && item.done>=item.total && output.exists()) {
                    item.state="Complete";item.speed=0;item.detail="Ready to play or save a copy."; break;
                }
                if(++tick%3==0) {
                    store.save();
                    CrashReports.checkpoint(this,"updating notification");
                    getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(item.title+" · "+(item.total>0?100*item.done/item.total:0)+"%"));
                }
                Thread.sleep(1000);
            }
        } catch(Exception e) {
            if(!item.state.equals("Paused")) {item.state="Error";item.detail=e instanceof IOException||e instanceof IllegalArgumentException?e.getMessage():"Download interrupted. Tap Resume to recheck and continue.";}
        } finally {
            // Keep data on pause/completion. Re-adding checks existing pieces before continuing.
            try{if(handle!=null && handle.isValid() && session!=null) {handle.pause();session.remove(handle);}}
            catch(Exception cleanup){if(!item.state.equals("Complete")&&!item.state.equals("Paused")){item.state="Error";item.detail="Torrent cleanup failed. Check Crash report before retrying.";}}
            item.speed=0;item.peers=0;item.seeds=0;store.save();
        }
    }
    @Override public void onTimeout(int startId,int fgsType) {store.pauseAll();destroyed=true;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy() {destroyed=true;getSystemService(ConnectivityManager.class).unregisterNetworkCallback(networkWatcher);worker.shutdown();super.onDestroy();}
}
