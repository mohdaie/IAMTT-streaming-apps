package com.iamtt.downloader;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import java.io.*;
import java.nio.file.Files;

/** Shared local folders only; never silently fall back to private video storage. */
public final class DownloadFiles {
    private DownloadFiles() {}
    public static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT>=30 ? Environment.isExternalStorageManager()
            : c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)==android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
    public static String defaultFolder(){return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"IAMTT").getAbsolutePath();}
    public static File folder(String path) throws IOException {
        File file=new File(path).getCanonicalFile();String p=file.getPath();
        if(!p.startsWith("/storage/")||p.contains("/Android/data")||p.contains("/Android/obb")||p.equals("/storage/emulated"))
            throw new IOException("Choose a local shared folder, such as Download/IAMTT.");
        return file;
    }
    public static File root(DownloadStore store,DownloadStore.Item i) throws IOException {
        if(!i.folder.isEmpty())return new File(i.folder);
        File base=folder(store.folder());
        File root=new File(base,"IAMTT-"+i.hash+"-"+i.requested);
        i.folder=root.getAbsolutePath();store.save();return root;
    }
    public static boolean privateVideo(Context c,DownloadStore.Item i){
        return !i.path.isEmpty()&&i.path.startsWith(c.getFilesDir().getAbsolutePath()+File.separator);
    }
    public static void moveCompleted(Context c,DownloadStore store,DownloadStore.Item i) throws IOException {
        File original=new File(i.path);if(!original.isFile())throw new IOException("The original video is missing.");
        File root=root(store,i);if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot create the selected folder.");
        File target=Protocol.safeFile(root,original.getName());
        if(target.exists())throw new IOException("A file already exists at the destination. Choose another folder.");
        if(root.getUsableSpace()<original.length()+64*1024*1024L)throw new IOException("Not enough space to move the video.");
        File temporary=new File(target.getPath()+".moving");
        try(FileInputStream in=new FileInputStream(original);FileOutputStream out=new FileOutputStream(temporary)){
            byte[] buffer=new byte[262144];int n;
            while((n=in.read(buffer))!=-1){if(i.deleteRequested||Thread.currentThread().isInterrupted())throw new IOException("Move cancelled.");out.write(buffer,0,n);}
            out.getFD().sync();
        }catch(IOException e){temporary.delete();throw e;}
        if(temporary.length()!=original.length()||!temporary.renameTo(target)){temporary.delete();throw new IOException("Could not finish moving the video.");}
        String oldPath=i.path;i.path=target.getAbsolutePath();i.moveRequested=false;i.state="Complete";i.detail="Saved in "+root.getParent();store.save();
        if(!new File(oldPath).delete())i.detail="Moved. The old app copy could not be deleted.";
    }
    public static void delete(Context c,DownloadStore store,DownloadStore.Item i) throws IOException {
        // folder is a dedicated per-job directory, never the user-selected base.
        if(!i.folder.isEmpty())erase(new File(i.folder));
        if(!i.path.isEmpty()&&new File(i.path).exists()){
            boolean shared=false;for(DownloadStore.Item other:store.all())if(other!=i&&other.path.equals(i.path))shared=true;
            if(!shared&&!new File(i.path).delete())throw new IOException("Could not delete the video. Check folder access and retry.");
        }
        boolean sameHash=false;for(DownloadStore.Item other:store.all())if(other!=i&&other.hash.equals(i.hash))sameHash=true;
        if(!sameHash){
            erase(new File(new File(c.getFilesDir(),"downloads"),i.hash));
            File oldExternal=c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if(oldExternal!=null)erase(new File(oldExternal,i.hash));
            new File(c.getFilesDir(),i.hash+".torrent").delete();
        }
        store.remove(i.id);
    }
    private static void erase(File f)throws IOException{
        if(!f.exists())return;
        if(Files.isSymbolicLink(f.toPath())){if(!f.delete())throw new IOException("Could not delete download link.");return;}
        if(f.isDirectory()){File[] children=f.listFiles();if(children==null)throw new IOException("Could not read the download folder.");for(File child:children)erase(child);}
        if(!f.delete())throw new IOException("Could not delete the download. Check folder access and retry.");
    }
}
