package com.iamtt.downloader;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.util.LruCache;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int INK=0xFFFFFFFF, MUTED=0xFF8E8E93, BLUE=0xFF0A84FF, BG=0xFF000000, CARD=0xFF111214, CARD2=0xFF1C1D20, DIVIDER=0xFF2A2B2F;
    private AddonClient addons;
    private DownloadStore downloads;
    private LinearLayout root,body,nav;
    private final ExecutorService io=Executors.newFixedThreadPool(6);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> artwork=new LruCache<>(40);
    private String tab="Discover",query="",mediaType="movie";
    private List<JSONObject> catalogueChoices=new ArrayList<>();
    private int catalogueIndex=0,nextSkip=0;
    private boolean canLoadMore=false;
    private String serverGenre="",titleSort="Newest";
    private final Map<String,String> titleFilters=new LinkedHashMap<>();
    private final Map<String,String> streamFilters=new LinkedHashMap<>();
    private LinearLayout catalogueArea;

    private int yearFilter=0,generation;
    private String exporting;
    private JSONObject activeSeries,activeMedia;
    private String downloadFilter="All";
    private List<JSONObject> currentTitles=new ArrayList<>();
    private List<JSONObject> currentStreams=new ArrayList<>();
    private LinearLayout sourceArea;
    private String sourceQuality="All",sourcePublisher="All",sourceSize="All",sourceSeeders="All",sourceSort="Best peers";
    private final Map<String,TextView> statuses=new HashMap<>();
    private final Map<String,ProgressBar> bars=new HashMap<>();
    private final Map<String,String> renderedStates=new HashMap<>();
    private final Map<String,Button> navButtons=new HashMap<>();
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

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();body.setPadding(dp(20),dp(12),dp(20),dp(28));scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        nav=new LinearLayout(this);nav.setPadding(dp(10),dp(6),dp(10),dp(8));nav.setBackgroundColor(0xFF080808);root.addView(nav);
        for(String name:new String[]{"Discover","Downloads","Addons"}) {
            Button b=navButton(name,()->show(name));navButtons.put(name,b);nav.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));
        }

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
    private GradientDrawable rounded(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}

    private Button button(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(INK);
        b.setBackground(rounded(CARD2,14));b.setOnClickListener(v->action.run());b.setPadding(dp(13),0,dp(13),0);return b;
    }
    private Button navButton(String label,Runnable action){
        Button b=button(label,action);b.setTextColor(MUTED);b.setBackgroundColor(Color.TRANSPARENT);b.setTextSize(12);return b;
    }
    private void refreshNav(){
        for(Map.Entry<String,Button> e:navButtons.entrySet()){
            boolean active=e.getKey().equals(tab)||(tab.equals("Sources")||tab.equals("Episodes"))&&e.getKey().equals("Discover");
            e.getValue().setTextColor(active?BLUE:MUTED);
            e.getValue().setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);
        }
    }
    private Button selectedButton(String label,boolean selected,Runnable action){
        Button b=button(label,action);b.setTextColor(INK);b.setBackground(rounded(selected?BLUE:CARD2,14));return b;
    }
    private LinearLayout card(){return card(body);}
    private LinearLayout card(ViewGroup parent){
        LinearLayout c=column();c.setPadding(dp(14),dp(14),dp(14),dp(14));c.setBackground(rounded(CARD,18));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);parent.addView(c,p);return c;
    }
    private void note(String s){body.addView(text(s,14,MUTED));}
    private void title(String s){TextView t=text(s,30,INK);t.setTypeface(null,Typeface.BOLD);body.addView(t);}
    private void section(String s){TextView t=text(s,21,INK);t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(18),0,dp(9));body.addView(t);}
    private void error(String s){new AlertDialog.Builder(this).setTitle("Could not finish").setMessage(s).setPositiveButton("OK",null).show();}

    private void show(String name){
        tab=name;generation++;body.removeAllViews();statuses.clear();bars.clear();renderedStates.clear();refreshNav();
        if(name.equals("Addons"))showAddons();else if(name.equals("Downloads"))showDownloads();else discover();
    }

    private void brandBar(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(4),0,dp(18));
        ImageView logo=new ImageView(this);logo.setImageResource(com.iamtt.downloader.R.drawable.iamtt_wordmark);logo.setScaleType(ImageView.ScaleType.FIT_START);
        row.addView(logo,new LinearLayout.LayoutParams(0,dp(40),1));
        TextView version=text("v"+BuildConfig.VERSION_NAME,11,MUTED);version.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);row.addView(version,new LinearLayout.LayoutParams(dp(90),dp(40)));
        body.addView(row);
    }

    private void showAddons(){
        brandBar();title("Addons");note("Connect catalogues and source addons. Settings stay on this device.");
        LinearLayout quick=card();quick.addView(text("Your sources",20,INK));
        quick.addView(button("Paste addon link",()->installDialog("")));
        quick.addView(button("Add Cinemeta catalogue",()->install(AddonClient.CATALOG)));
        quick.addView(button("Configure Torrentio",()->{
            new AlertDialog.Builder(this).setTitle("Torrentio setup")
                .setMessage("Configure Torrentio in your browser, then tap Install or copy its addon link and paste it here. Use sources you are authorized to download.")
                .setPositiveButton("Open configuration",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://torrentio.strem.fun/configure"))))
                .setNegativeButton("Cancel",null).show();
        }));
        JSONArray list=addons.installed();
        if(list.length()==0)note("No addons installed yet. Cinemeta supplies movie/TV artwork and metadata; your stream addon supplies sources.");
        for(int i=0;i<list.length();i++)try{
            JSONObject addon=list.getJSONObject(i),m=addon.getJSONObject("manifest");String id=m.getString("id");
            LinearLayout c=card();c.addView(text(m.optString("name"),18,INK));
            c.addView(text(Uri.parse(addon.getString("url")).getHost()+" · v"+m.optString("version","?"),12,MUTED));
            String caps=(Protocol.resource(m,"catalog","movie",null)?"Movies  ":"")+(Protocol.resource(m,"catalog","series",null)?"TV  ":"")
                +(Protocol.resource(m,"stream","movie",null)||Protocol.resource(m,"stream","series",null)?"Download sources":"");
            c.addView(text(caps.trim(),13,MUTED));
            c.addView(button("Remove",()->new AlertDialog.Builder(this).setTitle("Remove "+m.optString("name")+"?")
                .setPositiveButton("Remove",(d,w)->{try{addons.remove(id);show("Addons");}catch(Exception e){error("Could not remove addon.");}})
                .setNegativeButton("Cancel",null).show()));
        }catch(JSONException ignored){}
        note("Configured addon links may contain private tokens. IAMTT hides them from the addon list.");
    }

    private void installDialog(String initial){
        EditText input=new EditText(this);input.setSingleLine();input.setHint("https://…/manifest.json");input.setText(initial);input.setTextColor(INK);input.setHintTextColor(MUTED);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        new AlertDialog.Builder(this).setTitle("Install addon").setMessage("Paste the addon manifest link.").setView(input)
            .setPositiveButton("Install",(d,w)->install(input.getText().toString())).setNegativeButton("Cancel",null).show();
    }

    private void install(String url){
        try{Protocol.manifestUrl(url);}catch(Exception e){error(e.getMessage());return;}
        Toast.makeText(this,"Checking addon…",Toast.LENGTH_SHORT).show();
        io.execute(()->{try{addons.install(url);runOnUiThread(()->{if(!isDestroyed()){show("Addons");Toast.makeText(this,"Addon installed",Toast.LENGTH_SHORT).show();}});}
            catch(Exception e){runOnUiThread(()->{if(!isDestroyed())error("Unable to install addon. Check the manifest link and internet connection.");});}});
    }

    private void discover(){
        brandBar();
        title("Watch");note("Discover and download your favorite movies and TV shows.");

        LinearLayout typeRow=new LinearLayout(this);typeRow.setPadding(0,dp(10),0,dp(12));
        typeRow.addView(selectedButton("Movies",mediaType.equals("movie"),()->switchType("movie")),new LinearLayout.LayoutParams(0,dp(42),1));
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(dp(8),1);typeRow.addView(new Space(this),gap);
        typeRow.addView(selectedButton("TV Shows",mediaType.equals("series"),()->switchType("series")),new LinearLayout.LayoutParams(0,dp(42),1));
        body.addView(typeRow);

        EditText field=new EditText(this);field.setSingleLine();field.setHint("Search movies, TV shows, or genres…");
        field.setText(query);field.setTextColor(INK);field.setHintTextColor(MUTED);field.setBackground(rounded(CARD2,14));field.setPadding(dp(16),0,dp(16),0);
        body.addView(field,new LinearLayout.LayoutParams(-1,dp(48)));
        Runnable find=()->{query=field.getText().toString().trim();yearFilter=0;catalogueIndex=0;serverGenre="";titleFilters.clear();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(),0);show("Discover");};
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);field.setOnEditorActionListener((v,a,e)->{find.run();return true;});

        if(addons.installed().length()==0){
            LinearLayout c=card();c.addView(text("Start with your addons",19,INK));c.addView(text("Add Cinemeta for movie and TV artwork/metadata, then add a source addon.",14,MUTED));
            c.addView(button("Set up addons",()->show("Addons")));return;
        }

        TextView loading=text(mediaType.equals("series")?"Loading TV shows…":"Loading movies…",14,MUTED);loading.setPadding(0,dp(18),0,0);body.addView(loading);
        final int token=generation;final String requestedType=mediaType,requestedQuery=query,requestedGenre=serverGenre;final int requestedIndex=catalogueIndex;
        io.execute(()->{try{
            List<JSONObject> choices=addons.catalogs(requestedType,!requestedQuery.isEmpty());
            int index=requestedIndex>=choices.size()?0:requestedIndex;
            String genre=requestedGenre.isEmpty()&&!choices.isEmpty()?AddonClient.defaultGenre(choices.get(index)):requestedGenre;
            AddonClient.Results result=choices.isEmpty()?new AddonClient.Results():addons.catalog(choices.get(index),requestedType,requestedQuery,0,genre);
            runOnUiThread(()->{
                if(token!=generation||isDestroyed())return;body.removeView(loading);currentTitles=new ArrayList<>(result.items);catalogueChoices=choices;catalogueIndex=index;serverGenre=genre;
                nextSkip=result.items.size();canLoadMore=!result.items.isEmpty();
                catalogueArea=column();body.addView(catalogueArea);renderCatalogueContents();
            });
        }catch(Exception e){runOnUiThread(()->{if(token==generation)loading.setText("Catalogue failed. Check your connection and try again.");});}});
    }

    private void switchType(String type){
        if(mediaType.equals(type))return;mediaType=type;query="";yearFilter=0;catalogueIndex=0;serverGenre="";titleFilters.clear();activeSeries=null;activeMedia=null;currentTitles.clear();show("Discover");
    }

    private void renderCatalogueContents(){
        catalogueArea.removeAllViews();
        if(catalogueChoices.isEmpty()){catalogueArea.addView(text("No compatible catalogues. Add a catalogue in Addons.",14,MUTED));return;}
        JSONObject selected=catalogueChoices.get(catalogueIndex);
        catalogueArea.addView(button(selected.optString("label")+" ▾",()->{
            String[] labels=new String[catalogueChoices.size()];for(int i=0;i<labels.length;i++)labels[i]=catalogueChoices.get(i).optString("label");
            new AlertDialog.Builder(this).setTitle("Browse catalogue").setSingleChoiceItems(labels,catalogueIndex,(d,w)->{
                catalogueIndex=w;serverGenre="";yearFilter=0;titleFilters.clear();d.dismiss();show("Discover");
            }).setNegativeButton("Cancel",null).show();
        }));
        HorizontalScrollView sc=new HorizontalScrollView(this);LinearLayout chips=new LinearLayout(this);sc.addView(chips);
        addChip(chips,"Filters · "+(titleFilters.size()+(yearFilter>0?1:0)),this::chooseTitleFilter);
        addChip(chips,"Sort · "+titleSort,()->pick("Sort",Arrays.asList("Newest","Oldest","Title A–Z","Rating high–low"),titleSort,v->{titleSort=v;renderCatalogueContents();}));
        if(AddonClient.supports(selected,"genre"))addChip(chips,"Genre · "+(serverGenre.isEmpty()?"All":serverGenre),()->{
            List<String> values=new ArrayList<>();if(AddonClient.defaultGenre(selected).isEmpty())values.add("All");JSONArray es=selected.optJSONArray("extra");
            if(es!=null)for(int i=0;i<es.length();i++){JSONObject e=es.optJSONObject(i);if(e!=null&&"genre".equals(e.optString("name"))){JSONArray opts=e.optJSONArray("options");if(opts!=null)for(int j=0;j<opts.length();j++)values.add(opts.optString(j));}}
            pick("Catalogue genre",values,serverGenre.isEmpty()?"All":serverGenre,v->{serverGenre="All".equals(v)?"":v;titleFilters.clear();show("Discover");});
        });
        addChip(chips,"Reset",()->{titleFilters.clear();yearFilter=0;serverGenre="";titleSort="Newest";show("Discover");});
        catalogueArea.addView(sc);catalogueArea.addView(text("Filters apply to loaded titles; load more to expand results.",12,MUTED));
        List<JSONObject> list=new ArrayList<>();for(JSONObject m:currentTitles){
            if(yearFilter>0&&Protocol.year(m)!=yearFilter)continue;boolean matches=true;
            for(Map.Entry<String,String> f:titleFilters.entrySet())if(!attributeValues(m,f.getKey()).contains(f.getValue()))matches=false;
            if(matches)list.add(m);
        }
        list.sort((a,b)->{
            if(titleSort.equals("Title A–Z"))return a.optString("name").compareToIgnoreCase(b.optString("name"));
            if(titleSort.equals("Rating high–low"))return Double.compare(b.optDouble("imdbRating",-1),a.optDouble("imdbRating",-1));
            int cmp=Integer.compare(Protocol.year(b),Protocol.year(a));return titleSort.equals("Oldest")?-cmp:cmp;
        });
        catalogueArea.addView(text(list.size()+" of "+currentTitles.size()+" loaded titles",13,MUTED));
        LinearLayout grid=column();catalogueArea.addView(grid);
        for(int i=0;i<list.size();i+=3){LinearLayout row=new LinearLayout(this);grid.addView(row);
            for(int j=i;j<Math.min(i+3,list.size());j++){
                JSONObject media=list.get(j);LinearLayout tile=column();LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(3),dp(8),dp(3),dp(8));row.addView(tile,tp);
                ImageView poster=posterView(100,150);tile.addView(poster,new LinearLayout.LayoutParams(-1,dp(158)));loadImage(poster,media.optString("poster"),generation);
                TextView n=text(media.optString("name"),13,INK);n.setMaxLines(2);tile.addView(n);
                tile.addView(text(Protocol.year(media)+" · ★ "+media.optString("imdbRating","—"),11,MUTED));
                tile.setOnClickListener(v->{activeMedia=media;if(mediaType.equals("series")){activeSeries=media;episodes(media);}else{activeSeries=null;sources("movie",media.optString("id"),media.optString("name"));}});
            }
        }
        if(list.isEmpty())catalogueArea.addView(text("No titles match. Reset filters or load more.",14,MUTED));
        if(canLoadMore&&AddonClient.supports(selected,"skip"))catalogueArea.addView(button("Load more titles",this::loadMoreTitles));
        else catalogueArea.addView(text("End of this catalogue. Choose another catalogue above.",13,MUTED));
    }
    private void loadMoreTitles(){
        int token=generation;JSONObject c=catalogueChoices.get(catalogueIndex);int offset=nextSkip;
        catalogueArea.getChildAt(catalogueArea.getChildCount()-1).setEnabled(false);
        io.execute(()->{try{AddonClient.Results r=addons.catalog(c,mediaType,query,offset,serverGenre);main.post(()->{
            if(token!=generation||isDestroyed())return;
            Set<String> seen=new HashSet<>();for(JSONObject m:currentTitles)seen.add(m.optString("id"));int added=0;
            for(JSONObject m:r.items)if(seen.add(m.optString("id"))){currentTitles.add(m);added++;}
            nextSkip+=r.items.size();canLoadMore=added>0;renderCatalogueContents();
        });}catch(Exception e){main.post(()->{if(token==generation){renderCatalogueContents();error("Could not load more. Retry after checking your connection.");}});}});
    }
    private void addChip(LinearLayout row,String label,Runnable action){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(44));p.setMargins(0,dp(10),dp(8),dp(8));row.addView(button(label,action),p);}
    private interface Selection{void choose(String value);}
    private void pick(String label,List<String> values,String selected,Selection action){
        new AlertDialog.Builder(this).setTitle(label).setSingleChoiceItems(values.toArray(new String[0]),values.indexOf(selected),(d,w)->{d.dismiss();action.choose(values.get(w));}).setNegativeButton("Cancel",null).show();
    }
    private List<String> attributeValues(JSONObject media,String key){
        List<String> values=new ArrayList<>();Object v=media.opt(key);
        if(v instanceof JSONArray){JSONArray a=(JSONArray)v;for(int i=0;i<a.length();i++){Object x=a.opt(i);if(x instanceof String||x instanceof Number||x instanceof Boolean)values.add(String.valueOf(x));}}
        else if(v instanceof String||v instanceof Number||v instanceof Boolean){String x=String.valueOf(v);if(!x.isEmpty()&&!x.startsWith("http")&&x.length()<150)values.add(x);}
        return values;
    }
    private void chooseTitleFilter(){
        TreeSet<String> keys=new TreeSet<>();
        Set<String> excluded=new HashSet<>(Arrays.asList("id","type","poster","background","logo","description","name","slug","imdb_id","moviedb_id"));
        for(JSONObject m:currentTitles){Iterator<String> it=m.keys();while(it.hasNext()){String key=it.next();if(!excluded.contains(key)&&!attributeValues(m,key).isEmpty())keys.add(key);}}
        List<String> fields=new ArrayList<>();fields.add("Year");fields.addAll(keys);
        pick("Filter by movie attribute",fields,"",field->{
            TreeSet<String> opts=new TreeSet<>();for(JSONObject m:currentTitles){if(field.equals("Year")){if(Protocol.year(m)>0)opts.add(String.valueOf(Protocol.year(m)));}else opts.addAll(attributeValues(m,field));}
            List<String> values=new ArrayList<>();values.add("All");values.addAll(opts);
            pick(field,values,field.equals("Year")?(yearFilter==0?"All":String.valueOf(yearFilter)):titleFilters.getOrDefault(field,"All"),value->{
                if(field.equals("Year"))yearFilter=value.equals("All")?0:Integer.parseInt(value);
                else if(value.equals("All"))titleFilters.remove(field);else titleFilters.put(field,value);
                renderCatalogueContents();
            });
        });
    }

    private void renderMediaRail(String heading,List<JSONObject> items){
        section(heading);
        HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);strip.setFillViewport(false);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,0,dp(4),dp(4));strip.addView(row);
        for(JSONObject media:items){
            LinearLayout tile=column();tile.setPadding(0,0,0,dp(4));tile.setClickable(true);
            ImageView poster=posterView(118,176);tile.addView(poster,new LinearLayout.LayoutParams(dp(118),dp(176)));loadImage(poster,media.optString("poster"),generation);
            TextView name=text(media.optString("name","Untitled"),14,INK);name.setTypeface(null,Typeface.BOLD);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(name);
            int year=Protocol.year(media);String rating=media.optString("imdbRating","");
            tile.addView(text((year>0?String.valueOf(year):"")+(rating.isEmpty()?"":" · ★ "+rating),11,MUTED));
            tile.setOnClickListener(v->{activeMedia=media;if(mediaType.equals("series")){activeSeries=media;episodes(media);}else{activeSeries=null;sources("movie",media.optString("id"),media.optString("name","Movie"));}});
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(dp(118),-2);tp.rightMargin=dp(10);row.addView(tile,tp);
        }
        body.addView(strip,new LinearLayout.LayoutParams(-1,-2));
    }

    private ImageView posterView(int w,int h){
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(rounded(CARD2,14));image.setClipToOutline(true);
        image.setImageDrawable(null);return image;
    }

    private void loadImage(ImageView target,String url,int token){
        if(url==null||!url.startsWith("https://"))return;
        Bitmap cached=artwork.get(url);if(cached!=null){target.setImageBitmap(cached);return;}
        io.execute(()->{try{
            byte[] bytes=addons.image(url);Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap==null)return;artwork.put(url,bitmap);
            main.post(()->{if(token==generation&&!isDestroyed())target.setImageBitmap(bitmap);});
        }catch(Exception ignored){}});
    }

    private void episodes(JSONObject series){
        activeSeries=series;activeMedia=series;generation++;tab="Episodes";int token=generation;body.removeAllViews();refreshNav();
        brandBar();body.addView(button("‹ Back to TV Shows",()->show("Discover")));title(series.optString("name","TV Show"));
        LinearLayout hero=card();hero.setOrientation(LinearLayout.HORIZONTAL);
        ImageView p=posterView(100,150);hero.addView(p,new LinearLayout.LayoutParams(dp(100),dp(150)));loadImage(p,series.optString("poster"),token);
        LinearLayout hinfo=column();hinfo.setPadding(dp(14),0,0,0);hero.addView(hinfo,new LinearLayout.LayoutParams(0,-2,1));
        hinfo.addView(text(Protocol.year(series)>0?String.valueOf(Protocol.year(series)):"Series",13,MUTED));
        hinfo.addView(text("Choose an episode. IAMTT downloads only the selected episode file when the addon supplies a file index.",14,INK));

        TextView loading=text("Loading seasons and episodes…",14,MUTED);body.addView(loading);
        io.execute(()->{try{
            JSONObject meta=addons.meta("series",series.getString("id"));JSONArray videos=meta.optJSONArray("videos");
            List<JSONObject> eps=new ArrayList<>();if(videos!=null)for(int i=0;i<videos.length();i++)if(videos.optJSONObject(i)!=null)eps.add(videos.optJSONObject(i));
            eps.sort((a,b)->{int s=Integer.compare(a.optInt("season"),b.optInt("season"));return s!=0?s:Integer.compare(a.optInt("episode"),b.optInt("episode"));});
            runOnUiThread(()->{
                if(token!=generation||isDestroyed())return;body.removeView(loading);if(eps.isEmpty()){note("No episode list was returned for this series.");return;}
                int lastSeason=Integer.MIN_VALUE;
                for(JSONObject ep:eps){
                    int season=ep.optInt("season"),episode=ep.optInt("episode");
                    if(season!=lastSeason){section(season==0?"Specials":"Season "+season);lastSeason=season;}
                    episodeCard(series,ep,season,episode);
                }
            });
        }catch(Exception e){runOnUiThread(()->{if(token==generation)loading.setText("Could not load episodes. Check the catalogue addon and retry.");});}});
    }

    private void episodeCard(JSONObject series,JSONObject ep,int season,int episode){
        LinearLayout c=card();c.setOrientation(LinearLayout.HORIZONTAL);
        ImageView thumb=posterView(128,72);c.addView(thumb,new LinearLayout.LayoutParams(dp(128),dp(72)));loadImage(thumb,ep.optString("thumbnail"),generation);
        LinearLayout info=column();info.setPadding(dp(12),0,0,0);c.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        String tag=String.format(Locale.US,"S%02dE%02d",season,episode);
        String episodeName=ep.optString("name",ep.optString("title","Episode "+episode));
        TextView n=text(tag+"  "+episodeName,16,INK);n.setTypeface(null,Typeface.BOLD);info.addView(n);
        String released=ep.optString("released","");if(released.length()>=10)released=released.substring(0,10);if(!released.isEmpty())info.addView(text(released,12,MUTED));
        String id=ep.optString("id");String downloadTitle=series.optString("name","Series")+" - "+tag+" - "+episodeName;
        if(!id.isEmpty())info.addView(button("Find sources",()->sources("series",id,downloadTitle)));
    }

    private void sources(String type,String id,String displayTitle){
        generation++;tab="Sources";int token=generation;body.removeAllViews();refreshNav();
        sourceQuality="All";sourcePublisher="All";sourceSize="All";sourceSeeders="All";sourceSort="Best peers";streamFilters.clear();currentStreams.clear();
        body.addView(button(type.equals("series")?"‹ Back to episodes":"‹ Back to movies",()->{if(type.equals("series")&&activeSeries!=null)episodes(activeSeries);else show("Discover");}));
        if(activeMedia!=null){
            LinearLayout hero=column();hero.setPadding(0,dp(12),0,dp(10));
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);
            ImageView poster=posterView(92,138);row.addView(poster,new LinearLayout.LayoutParams(dp(92),dp(138)));loadImage(poster,activeMedia.optString("poster"),token);
            LinearLayout info=column();info.setPadding(dp(14),0,0,0);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            TextView h=text(displayTitle,22,INK);h.setTypeface(null,Typeface.BOLD);info.addView(h);
            int y=Protocol.year(activeMedia);String rating=activeMedia.optString("imdbRating","");
            info.addView(text((y>0?String.valueOf(y):"")+(rating.isEmpty()?"":" · ★ "+rating),12,MUTED));
            String desc=activeMedia.optString("description","");if(!desc.isEmpty()){TextView d=text(desc,13,MUTED);d.setMaxLines(4);d.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(d);}
            hero.addView(row);body.addView(hero);
        }else title(displayTitle);
        TextView loading=text("Finding available sources…",14,MUTED);body.addView(loading);
        sourceArea=column();body.addView(sourceArea,new LinearLayout.LayoutParams(-1,-2));
        io.execute(()->{try{
            AddonClient.Results result=addons.streams(type,id);
            runOnUiThread(()->{
                if(token!=generation||isDestroyed())return;body.removeView(loading);for(String e:result.errors)note(e);
                currentStreams=new ArrayList<>(result.items);
                if(currentStreams.isEmpty())note("No sources were returned for this title.");
                renderSourceArea(type,displayTitle);
            });
        }catch(Exception e){runOnUiThread(()->{if(token==generation)loading.setText("Source lookup failed. Go back and retry.");});}});
    }

    private void renderSourceArea(String type,String displayTitle){
        if(sourceArea==null)return;sourceArea.removeAllViews();if(currentStreams.isEmpty())return;
        HorizontalScrollView filters=new HorizontalScrollView(this);filters.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);filters.addView(chips);
        Button q=button("Quality · "+sourceQuality,()->chooseSourceQuality(type,displayTitle));
        Button p=button("Source · "+sourcePublisher,()->chooseSourcePublisher(type,displayTitle));
        Button s=button("Size · "+sourceSize,()->chooseSourceSize(type,displayTitle));
        Button seedFilter=button("Seeders · "+sourceSeeders,()->chooseSourceSeeders(type,displayTitle));
        Button sort=button("Sort · "+sourceSort,()->chooseSourceSort(type,displayTitle));
        for(Button b:new Button[]{q,p,s,seedFilter,sort}){LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(42));cp.rightMargin=dp(8);chips.addView(b,cp);}
        addChip(chips,"More filters · "+streamFilters.size(),()->{
            List<String> fields=new ArrayList<>(Arrays.asList("Codec","Release format","Dynamic range","Audio","Language"));
            pick("Source attributes",fields,"",key->{
                TreeSet<String> set=new TreeSet<>();for(JSONObject stream:currentStreams)set.addAll(Protocol.sourceAttributes(stream,key));
                List<String> values=new ArrayList<>();values.add("All");values.addAll(set);
                pick(key,values,streamFilters.getOrDefault(key,"All"),value->{if(value.equals("All"))streamFilters.remove(key);else streamFilters.put(key,value);renderSourceArea(type,displayTitle);});
            });
        });
        addChip(chips,"Reset",()->{sourceQuality="All";sourcePublisher="All";sourceSize="All";sourceSeeders="All";streamFilters.clear();renderSourceArea(type,displayTitle);});
        sourceArea.addView(filters,new LinearLayout.LayoutParams(-1,dp(64)));

        List<JSONObject> list=new ArrayList<>();
        for(JSONObject stream:currentStreams){
            if(!"All".equals(sourceQuality)&&!sourceQuality.equals(Protocol.sourceQuality(stream)))continue;
            if(!"All".equals(sourcePublisher)&&!sourcePublisher.equals(Protocol.sourcePublisher(stream)))continue;
            if(!sourceSizeMatches(stream)||!sourceSeederMatches(stream))continue;
            boolean matches=true;for(Map.Entry<String,String> f:streamFilters.entrySet())if(!Protocol.sourceAttributes(stream,f.getKey()).contains(f.getValue()))matches=false;
            if(!matches)continue;
            list.add(stream);
        }
        list.sort((a,b)->{
            long sa=Protocol.sourceSize(a),sb=Protocol.sourceSize(b);
            if("Smallest".equals(sourceSort)){
                if(sa<=0&&sb<=0)return 0;if(sa<=0)return 1;if(sb<=0)return -1;return Long.compare(sa,sb);
            }
            if("Largest".equals(sourceSort)){
                if(sa<=0&&sb<=0)return 0;if(sa<=0)return 1;if(sb<=0)return -1;return Long.compare(sb,sa);
            }
            return Integer.compare(Protocol.sourceHealthRank(b),Protocol.sourceHealthRank(a));
        });
        TextView count=text(list.size()+" of "+currentStreams.size()+" sources",12,MUTED);count.setPadding(0,dp(12),0,dp(8));sourceArea.addView(count);
        if(list.isEmpty()){sourceArea.addView(text("No torrent matches these filters.",14,MUTED));return;}
        for(JSONObject stream:list)sourceCard(sourceArea,stream,type,displayTitle);
    }

    private boolean sourceSizeMatches(JSONObject stream){
        if("All".equals(sourceSize))return true;long n=Protocol.sourceSize(stream);if(n<=0)return false;
        long gb=1073741824L;
        if("< 1 GB".equals(sourceSize))return n<gb;
        if("1–3 GB".equals(sourceSize))return n>=gb&&n<3*gb;
        if("3–8 GB".equals(sourceSize))return n>=3*gb&&n<8*gb;
        return n>=8*gb;
    }

    private void chooseSourceQuality(String type,String title){
        LinkedHashSet<String> set=new LinkedHashSet<>();set.add("All");for(JSONObject s:currentStreams)set.add(Protocol.sourceQuality(s));
        chooseSourceOption("Quality",new ArrayList<>(set),sourceQuality,v->{sourceQuality=v;renderSourceArea(type,title);});
    }
    private void chooseSourcePublisher(String type,String title){
        TreeSet<String> names=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);for(JSONObject s:currentStreams)names.add(Protocol.sourcePublisher(s));
        List<String> values=new ArrayList<>();values.add("All");values.addAll(names);
        chooseSourceOption("Torrent source",values,sourcePublisher,v->{sourcePublisher=v;renderSourceArea(type,title);});
    }
    private void chooseSourceSize(String type,String title){
        chooseSourceOption("File size",Arrays.asList("All","< 1 GB","1–3 GB","3–8 GB","8+ GB"),sourceSize,v->{sourceSize=v;renderSourceArea(type,title);});
    }
    private void chooseSourceSeeders(String type,String title){
        chooseSourceOption("Minimum reported seeders",Arrays.asList("All","1+","5+","20+"),sourceSeeders,v->{sourceSeeders=v;renderSourceArea(type,title);});
    }
    private boolean sourceSeederMatches(JSONObject stream){
        if("All".equals(sourceSeeders))return true;int seeds=Protocol.reportedSeeders(stream);if(seeds<0)return false;
        if("1+".equals(sourceSeeders))return seeds>=1;if("5+".equals(sourceSeeders))return seeds>=5;return seeds>=20;
    }
    private void chooseSourceSort(String type,String title){
        chooseSourceOption("Sort torrents",Arrays.asList("Best peers","Smallest","Largest"),sourceSort,v->{sourceSort=v;renderSourceArea(type,title);});
    }
    private interface ChoiceHandler{void set(String value);}
    private void chooseSourceOption(String title,List<String> values,String selected,ChoiceHandler handler){
        String[] labels=values.toArray(new String[0]);int checked=Math.max(0,values.indexOf(selected));
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,checked,(d,which)->{handler.set(values.get(which));d.dismiss();}).setNegativeButton("Cancel",null).show();
    }

    private void sourceCard(ViewGroup parent,JSONObject stream,String type,String displayTitle){
        LinearLayout c=card(parent);
        String quality=Protocol.sourceQuality(stream),codec=Protocol.sourceCodec(stream),publisher=Protocol.sourcePublisher(stream);
        long bytes=Protocol.sourceSize(stream);int seeds=Protocol.reportedSeeders(stream);String health=Protocol.sourceHealth(stream);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView head=text(quality+(codec.isEmpty()?"":" · "+codec),16,INK);head.setTypeface(null,Typeface.BOLD);top.addView(head,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(text(bytes>0?size(bytes):"Unknown size",13,MUTED));c.addView(top);
        c.addView(text(publisher+" · "+(seeds>=0?seeds+" seeders":"seeders not reported")+" · "+health,12,seeds==0?0xFFFF9F0A:MUTED));
        String raw=stream.optString("description",stream.optString("title",""));
        if(!raw.isEmpty()){TextView d=text(raw,12,MUTED);c.addView(d);}
        boolean torrent=stream.optString("infoHash").matches("(?i)[0-9a-f]{40}");
        if(torrent){Button dl=button(seeds==0?"Try anyway":"Download",()->enqueue(stream,displayTitle));dl.setBackground(rounded(BLUE,14));c.addView(dl,new LinearLayout.LayoutParams(-1,dp(44)));}
        else c.addView(text("Direct/debrid streams are not handled by this downloader build.",13,MUTED));
    }

    private void enqueue(JSONObject stream,String title){
        try{downloads.add(stream,title);DownloadService.start(this);show("Downloads");}
        catch(Exception e){error(e instanceof IllegalArgumentException?e.getMessage():"Could not start the download. Check notification permissions and retry from Downloads.");}
    }

    private void showDownloads(){
        brandBar();title("Downloads");

        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(0,dp(8),0,dp(12));
        for(String name:new String[]{"All","Downloading","Completed","Paused"}){
            Button b=selectedButton(name,name.equals(downloadFilter),()->{downloadFilter=name;show("Downloads");});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(40),1);if(tabs.getChildCount()>0)p.leftMargin=dp(6);tabs.addView(b,p);
        }
        body.addView(tabs);

        Switch wifi=new Switch(this);wifi.setText("Wi-Fi only");wifi.setTextColor(MUTED);wifi.setChecked(downloads.wifiOnly());
        wifi.setPadding(0,0,0,dp(12));wifi.setOnCheckedChangeListener((b,on)->downloads.wifiOnly(on));body.addView(wifi);

        List<DownloadStore.Item> visible=new ArrayList<>();for(DownloadStore.Item i:downloads.all())if(downloadVisible(i))visible.add(i);
        if(visible.isEmpty()){note(downloads.all().isEmpty()?"Your downloads will appear here after you choose a source.":"No downloads in this view.");return;}
        for(DownloadStore.Item i:visible){
            LinearLayout c=card();
            LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=text(i.title,17,INK);name.setTypeface(null,Typeface.BOLD);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            TextView state=text(downloadGroup(i),12,i.state.equals("Complete")?0xFF30D158:(i.state.equals("Error")?0xFFFF9F0A:BLUE));state.setGravity(Gravity.RIGHT);top.addView(state);c.addView(top);

            TextView status=text(progress(i),12,MUTED);c.addView(status);statuses.put(i.id,status);
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);c.addView(bar,new LinearLayout.LayoutParams(-1,dp(6)));bars.put(i.id,bar);renderedStates.put(i.id,i.state);

            LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(10),0,0);
            if(i.state.equals("Complete")){
                Button play=button("Play",()->open(i,false));Button save=button("Save a copy",()->export(i));
                actions.addView(play,new LinearLayout.LayoutParams(0,dp(42),1));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(42),1);ap.leftMargin=dp(8);actions.addView(save,ap);
            }else if(i.state.equals("Paused")||i.state.equals("Error")){
                Button resume=button("Resume",()->{i.state="Queued";i.detail="";downloads.save();try{DownloadService.start(this);show("Downloads");}
                    catch(Exception e){i.state="Paused";downloads.save();error("Could not restart. Reopen the app and try again.");}});
                actions.addView(resume,new LinearLayout.LayoutParams(-1,dp(42)));
            }else{
                Button pause=button("Pause",()->{i.state="Paused";i.speed=0;downloads.save();show("Downloads");});
                actions.addView(pause,new LinearLayout.LayoutParams(-1,dp(42)));
            }
            c.addView(actions);
        }
        refreshProgress();
    }

    private String downloadGroup(DownloadStore.Item i){
        if(i.state.equals("Complete"))return "Completed";
        if(i.state.equals("Paused")||i.state.equals("Error"))return "Paused";
        return "Downloading";
    }
    private boolean downloadVisible(DownloadStore.Item i){return downloadFilter.equals("All")||downloadFilter.equals(downloadGroup(i));}

    private String progress(DownloadStore.Item i){
        long total=i.total>0?i.total:i.expectedTotal;String seedText=i.reportedSeeds>=0?String.valueOf(i.reportedSeeds):"—";
        long percent=total>0?Math.min(100,100*i.done/total):0;
        String first=size(i.done)+" / "+(total>0?size(total):"unknown")+" · "+size(i.speed)+"/s · "+percent+"%";
        String second="Peers "+i.peers+" · Seeders "+seedText;
        String detail=i.detail==null?"":i.detail;
        return first+"\n"+second+(detail.isEmpty()?"":"\n"+detail);
    }
    private static String size(long n){if(n<=0)return "0 MB";return String.format(Locale.US,n>=1073741824?"%.2f GB":"%.1f MB",n/(n>=1073741824?1073741824.0:1048576.0));}

    private void refreshProgress(){
        for(DownloadStore.Item i:downloads.all()){
            if(!downloadVisible(i))continue;
            String old=renderedStates.get(i.id);boolean oldControls="Complete".equals(old)||"Paused".equals(old)||"Error".equals(old);
            boolean controls=i.state.equals("Complete")||i.state.equals("Paused")||i.state.equals("Error");
            if(old==null||(!i.state.equals(old)&&(oldControls||controls))){show("Downloads");return;}
            TextView t=statuses.get(i.id);if(t!=null)t.setText(progress(i));ProgressBar b=bars.get(i.id);
            if(b!=null){long total=i.total>0?i.total:i.expectedTotal;b.setIndeterminate(total==0&&!i.state.equals("Error")&&!i.state.equals("Paused"));b.setProgress(total>0?(int)Math.min(1000,1000*i.done/total):0);}
        }
    }

    private void open(DownloadStore.Item item,boolean share){
        try{
            File file=new File(item.path);if(!file.isFile())throw new IOException();Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",file);
            Intent intent=new Intent(share?Intent.ACTION_SEND:Intent.ACTION_VIEW);intent.setType("video/*");
            if(share)intent.putExtra(Intent.EXTRA_STREAM,uri);else intent.setDataAndType(uri,"video/*");
            intent.setClipData(ClipData.newRawUri("Video",uri));intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent,share?"Share video":"Play video"));
        }catch(Exception e){error("No compatible player is available, or the video file is missing.");}
    }

    private String exportName(DownloadStore.Item item){
        String extension="";String source=new File(item.path).getName();int dot=source.lastIndexOf('.');
        if(dot>=0&&source.length()-dot<=8)extension=source.substring(dot);
        String clean=item.title.replaceAll("[\\\\/:*?\"<>|]"," ").replaceAll("\\s+"," ").trim();
        return (clean.isEmpty()?"IAMTT Video":clean)+extension;
    }

    private void export(DownloadStore.Item item){
        exporting=item.path;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("video/*");
        intent.putExtra(Intent.EXTRA_TITLE,exportName(item));startActivityForResult(intent,42);
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=42||result!=RESULT_OK||data==null||data.getData()==null||exporting==null)return;
        String path=exporting;exporting=null;Uri destination=data.getData();Toast.makeText(this,"Saving copy—keep the app open…",Toast.LENGTH_LONG).show();
        io.execute(()->{try(InputStream in=new FileInputStream(path);OutputStream out=getContentResolver().openOutputStream(destination,"w")){
            if(out==null)throw new IOException();byte[] buffer=new byte[262144];int count;while((count=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException();out.write(buffer,0,count);}
            main.post(()->Toast.makeText(this,"Copy saved",Toast.LENGTH_LONG).show());
        }catch(Exception e){main.post(()->{if(!isDestroyed())error("Copy could not finish. Your original download is still available. Remove any incomplete copy and retry.");});}});
    }

    @Override public void onBackPressed(){
        if(tab.equals("Sources")){if(mediaType.equals("series")&&activeSeries!=null)episodes(activeSeries);else show("Discover");return;}
        if(tab.equals("Episodes")){show("Discover");return;}super.onBackPressed();
    }
}
