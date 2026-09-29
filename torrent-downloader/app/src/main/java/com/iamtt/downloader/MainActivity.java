package com.iamtt.downloader;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int INK=0xFF141820, MUTED=0xFF657084, BLUE=0xFF2563EB, BG=0xFFF5F7FB;
    private AddonClient addons;
    private DownloadStore downloads;
    private LinearLayout root,body,nav;
    private final ExecutorService io=Executors.newFixedThreadPool(3);
    private final Handler main=new Handler(Looper.getMainLooper());
    private String tab="Discover",query="";
    private int generation;
    private String exporting;
    private final Map<String,TextView> statuses=new HashMap<>();
    private final Map<String,ProgressBar> bars=new HashMap<>();
    private final Map<String,String> renderedStates=new HashMap<>();
    private final Runnable ticker=new Runnable(){public void run(){if(tab.equals("Downloads"))refreshProgress();main.postDelayed(this,1000);}};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);addons=new AddonClient(this);downloads=DownloadStore.get(this);
        if(state!=null) exporting=state.getString("exporting");
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());root.setPadding(i.left,i.top,i.right,i.bottom);}
            else root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        LinearLayout header=column();header.setPadding(dp(22),dp(20),dp(22),dp(10));
        TextView brand=text("IAMTT  /  Downloader",22,INK);brand.setTypeface(null,Typeface.BOLD);header.addView(brand);
        header.addView(text("Your sources. Your downloads.    v"+BuildConfig.VERSION_NAME,12,MUTED));root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();body.setPadding(dp(20),dp(12),dp(20),dp(24));scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this);nav.setPadding(dp(10),dp(8),dp(10),dp(10));root.addView(nav);
        for(String name:new String[]{"Discover","Downloads","Addons"}) {Button b=button(name,()->show(name));nav.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}
        show(getIntent().getBooleanExtra("downloads",false)?"Downloads":"Discover");
        handleIntent(getIntent());
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},41);
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleIntent(intent);}
    private void handleIntent(Intent i){if(i.getData()!=null && "stremio".equals(i.getData().getScheme())){show("Addons");installDialog(i.getData().toString());}}
    @Override protected void onResume(){super.onResume();main.post(ticker);}
    @Override protected void onPause(){main.removeCallbacks(ticker);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle b){b.putString("exporting",exporting);super.onSaveInstanceState(b);}
    @Override protected void onDestroy(){generation++;io.shutdownNow();super.onDestroy();}
    private LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setPadding(0,dp(4),0,dp(4));return t;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(BLUE);b.setOnClickListener(v->action.run());return b;}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(16),dp(12),dp(16),dp(14));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(18));c.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);body.addView(c,p);return c;}
    private void note(String s){body.addView(text(s,14,MUTED));}
    private void title(String s){TextView t=text(s,28,INK);t.setTypeface(null,Typeface.BOLD);body.addView(t);}
    private void error(String s){new AlertDialog.Builder(this).setTitle("Could not finish").setMessage(s).setPositiveButton("OK",null).show();}
    private void show(String name){tab=name;generation++;body.removeAllViews();statuses.clear();bars.clear();renderedStates.clear();if(name.equals("Addons"))showAddons();else if(name.equals("Downloads"))showDownloads();else discover();}

    private void showAddons(){
        title("Make it yours.");note("Install a catalogue for movies and a stream addon for download sources.");
        LinearLayout quick=card();quick.addView(text("Add your sources",19,INK));
        quick.addView(button("Paste addon link",()->installDialog("")));
        quick.addView(button("Add Cinemeta catalogue",()->install(AddonClient.CATALOG)));
        quick.addView(button("Configure Torrentio",()->{
            new AlertDialog.Builder(this).setTitle("Torrentio setup").setMessage("Configure Torrentio in your browser, then tap Install or copy its addon link and paste it here. For peer-to-peer downloads, leave Debrid Provider unset.")
                .setPositiveButton("Open configuration",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://torrentio.strem.fun/configure")))).setNegativeButton("Cancel",null).show();
        }));
        JSONArray list=addons.installed();
        if(list.length()==0)note("No addons installed yet. Add Cinemeta and your Torrentio link to begin.");
        for(int i=0;i<list.length();i++)try{
            JSONObject addon=list.getJSONObject(i),m=addon.getJSONObject("manifest");String id=m.getString("id");
            LinearLayout c=card();c.addView(text(m.optString("name"),18,INK));
            c.addView(text(Uri.parse(addon.getString("url")).getHost()+" · v"+m.optString("version","?"),12,MUTED));
            c.addView(text((Protocol.resource(m,"catalog","movie",null)?"Movie catalogue  ":"")+(Protocol.resource(m,"stream","movie",null)?"Download sources":""),13,MUTED));
            c.addView(button("Remove",()->new AlertDialog.Builder(this).setTitle("Remove "+m.optString("name")+"?").setPositiveButton("Remove",(d,w)->{try{addons.remove(id);show("Addons");}catch(Exception e){error("Could not remove addon.");}}).setNegativeButton("Cancel",null).show()));
        }catch(JSONException ignored){}
        note("Addon settings stay on this device. Configured links can contain private account keys; they are not shown in the addon list.");
    }
    private void installDialog(String initial){
        EditText input=new EditText(this);input.setSingleLine();input.setHint("https://…/manifest.json");input.setText(initial);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        new AlertDialog.Builder(this).setTitle("Install addon").setMessage("Paste the addon manifest link.").setView(input)
            .setPositiveButton("Install",(d,w)->install(input.getText().toString())).setNegativeButton("Cancel",null).show();
    }
    private void install(String url){
        try{Protocol.manifestUrl(url);}catch(Exception e){error(e.getMessage());return;}
        Toast.makeText(this,"Checking addon…",Toast.LENGTH_SHORT).show();
        io.execute(()->{try{addons.install(url);runOnUiThread(()->{if(!isDestroyed()){show("Addons");Toast.makeText(this,"Addon installed",Toast.LENGTH_SHORT).show();}});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed())error("Unable to install addon. Check the manifest link and internet connection.");});}});
    }
    private void discover(){
        title("Find your next film.");note("Search a title, choose a source, and save the video to your phone.");
        LinearLayout search=new LinearLayout(this);EditText field=new EditText(this);field.setSingleLine();field.setHint("Search movies");field.setText(query);
        search.addView(field,new LinearLayout.LayoutParams(0,dp(54),1));
        Runnable find=()->{query=field.getText().toString().trim();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(),0);show("Discover");};
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);field.setOnEditorActionListener((v,a,e)->{find.run();return true;});search.addView(button("Search",find));body.addView(search);
        if(addons.installed().length()==0){LinearLayout c=card();c.addView(text("Start with your addons",20,INK));c.addView(text("Add Cinemeta for movie search and Torrentio for sources.",14,MUTED));c.addView(button("Set up addons",()->show("Addons")));return;}
        TextView loading=text("Loading movies…",14,MUTED);body.addView(loading);final int token=generation;
        io.execute(()->{try{AddonClient.Results result=addons.movies(query);runOnUiThread(()->{
            if(token!=generation||isDestroyed())return;body.removeView(loading);
            for(String e:result.errors)note(e);
            if(result.items.isEmpty())note("No movies found. Try a different title or check your catalogue addon.");
            for(JSONObject m:result.items)movieCard(m);
        });}catch(Exception e){runOnUiThread(()->{if(token==generation)loading.setText("Search failed. Check your connection and try again.");});}});
    }
    private void movieCard(JSONObject movie){
        LinearLayout c=card();c.addView(text(movie.optString("name","Untitled"),20,INK));
        String info=movie.optString("releaseInfo",movie.optString("year",""));String rating=movie.optString("imdbRating","");
        c.addView(text(info+(rating.isEmpty()?"":"   ★ "+rating),13,MUTED));
        String description=movie.optString("description","");if(!description.isEmpty()){TextView d=text(description,14,MUTED);d.setMaxLines(3);d.setEllipsize(android.text.TextUtils.TruncateAt.END);c.addView(d);}
        c.addView(button("Find download sources",()->sources(movie)));
    }
    private void sources(JSONObject movie){
        generation++;tab="Sources";int token=generation;body.removeAllViews();
        body.addView(button("‹ Back to movies",()->show("Discover")));title(movie.optString("name"));
        TextView loading=text("Asking your stream addons…",14,MUTED);body.addView(loading);
        io.execute(()->{try{AddonClient.Results result=addons.streams(movie.getString("id"));runOnUiThread(()->{
            if(token!=generation||isDestroyed())return;body.removeView(loading);for(String e:result.errors)note(e);
            if(result.items.isEmpty())note("No sources returned for this movie.");
            for(JSONObject stream:result.items){
                LinearLayout c=card();c.addView(text(stream.optString("name",stream.optString("addonName")),18,INK));
                c.addView(text(stream.optString("description",stream.optString("title","")),14,MUTED));
                boolean torrent=stream.optString("infoHash").matches("(?i)[0-9a-f]{40}");
                if(torrent)c.addView(button("↓ Download to phone",()->enqueue(stream,movie.optString("name"))));
                else c.addView(text("This is a direct stream or an unsupported source. Use Torrentio with Debrid Provider unset for this torrent-only prototype.",13,MUTED));
            }
        });}catch(Exception e){runOnUiThread(()->{if(token==generation)loading.setText("Source lookup failed. Go back and retry.");});}});
    }
    private void enqueue(JSONObject stream,String title){
        try{downloads.add(stream,title);DownloadService.start(this);show("Downloads");}
        catch(Exception e){error(e instanceof IllegalArgumentException?e.getMessage():"Could not start the download. Check notification permissions and retry from Downloads.");}
    }
    private void showDownloads(){
        title("Your downloads.");
        Switch wifi=new Switch(this);wifi.setText("Download over Wi-Fi only");wifi.setChecked(downloads.wifiOnly());wifi.setPadding(0,dp(8),0,dp(14));wifi.setOnCheckedChangeListener((b,on)->downloads.wifiOnly(on));body.addView(wifi);
        note("Videos stay in this app until you choose Save a copy or Share. Uninstalling removes the app's copies.");
        if(downloads.all().isEmpty())note("Your downloads will appear here after you choose a source.");
        for(DownloadStore.Item i:downloads.all()){
            LinearLayout c=card();c.addView(text(i.title,19,INK));TextView status=text(progress(i),13,MUTED);c.addView(status);statuses.put(i.hash,status);
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);c.addView(bar);bars.put(i.hash,bar);renderedStates.put(i.hash,i.state);
            if(i.state.equals("Complete")){
                c.addView(button("Play",()->open(i,false)));c.addView(button("Save a copy…",()->export(i)));c.addView(button("Share…",()->open(i,true)));
            }else if(i.state.equals("Paused")||i.state.equals("Error")){
                c.addView(button("Resume",()->{i.state="Queued";i.detail="";downloads.save();try{DownloadService.start(this);show("Downloads");}catch(Exception e){i.state="Paused";downloads.save();error("Could not restart. Reopen the app and try again.");}}));
            }else c.addView(button("Pause",()->{i.state="Paused";i.speed=0;downloads.save();show("Downloads");}));
        }
        refreshProgress();
    }
    private String progress(DownloadStore.Item i){return i.state+" · "+size(i.done)+" / "+size(i.total)+"\n"+(i.speed>0?size(i.speed)+"/s · "+i.peers+" peers\n":"")+(i.detail==null?"":i.detail);}
    private static String size(long n){if(n<=0)return "0 MB";return String.format(Locale.US,n>=1073741824?"%.2f GB":"%.1f MB",n/(n>=1073741824?1073741824.0:1048576.0));}
    private void refreshProgress(){
        for(DownloadStore.Item i:downloads.all()){
            String old=renderedStates.get(i.hash);
            boolean oldControls="Complete".equals(old)||"Paused".equals(old)||"Error".equals(old);
            boolean controls=i.state.equals("Complete")||i.state.equals("Paused")||i.state.equals("Error");
            if(old==null||(!i.state.equals(old)&&(oldControls||controls))){show("Downloads");return;}
            TextView t=statuses.get(i.hash);if(t!=null)t.setText(progress(i));ProgressBar b=bars.get(i.hash);if(b!=null){b.setIndeterminate(i.total==0&&!i.state.equals("Error")&&!i.state.equals("Paused"));b.setProgress(i.total>0?(int)Math.min(1000,1000*i.done/i.total):0);}
        }
    }
    private void open(DownloadStore.Item item,boolean share){
        try{File file=new File(item.path);if(!file.isFile())throw new IOException();Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",file);
            Intent intent=new Intent(share?Intent.ACTION_SEND:Intent.ACTION_VIEW);intent.setType("video/*");
            if(share)intent.putExtra(Intent.EXTRA_STREAM,uri);else intent.setDataAndType(uri,"video/*");
            intent.setClipData(ClipData.newRawUri("Video",uri));intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(intent,share?"Share video":"Play video"));
        }catch(Exception e){error("No compatible player is available, or the video file is missing.");}
    }
    private void export(DownloadStore.Item item){exporting=item.path;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("video/*");intent.putExtra(Intent.EXTRA_TITLE,new File(item.path).getName());startActivityForResult(intent,42);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=42||result!=RESULT_OK||data==null||data.getData()==null||exporting==null)return;
        String path=exporting;exporting=null;Uri destination=data.getData();Toast.makeText(this,"Saving copy—keep the app open…",Toast.LENGTH_LONG).show();
        io.execute(()->{try(InputStream in=new FileInputStream(path);OutputStream out=getContentResolver().openOutputStream(destination,"w")){
            if(out==null)throw new IOException();byte[] buffer=new byte[262144];int count;while((count=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException();out.write(buffer,0,count);}
            main.post(()->Toast.makeText(this,"Copy saved",Toast.LENGTH_LONG).show());
        }catch(Exception e){main.post(()->{if(!isDestroyed())error("Copy could not finish. Your original download is still available. Remove any incomplete copy and retry.");});}});
    }
    @Override public void onBackPressed(){if(tab.equals("Sources")){show("Discover");return;}super.onBackPressed();}
}
