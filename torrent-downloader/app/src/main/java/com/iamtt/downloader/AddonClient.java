package com.iamtt.downloader;

import android.content.Context;
import org.json.*;
import okhttp3.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class AddonClient {
    public static final String CATALOG = "https://v3-cinemeta.strem.io/manifest.json";
    private final android.content.SharedPreferences prefs;
    private final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).followSslRedirects(false).build();

    public AddonClient(Context context) { prefs = context.getSharedPreferences("addons", Context.MODE_PRIVATE); }

    public synchronized JSONArray installed() {
        try { return new JSONArray(prefs.getString("items", "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    public synchronized void install(String input) throws Exception {
        String url = Protocol.manifestUrl(input);
        JSONObject manifest = get(url);
        if (manifest.optString("id").isEmpty() || manifest.optString("name").isEmpty() || manifest.optJSONArray("resources") == null)
            throw new IOException("This address did not return a valid addon manifest.");
        JSONArray existing = installed(), next = new JSONArray();
        for (int i = 0; i < existing.length(); i++) {
            JSONObject item = existing.getJSONObject(i);
            if (!item.getJSONObject("manifest").optString("id").equals(manifest.optString("id"))) next.put(item);
        }
        next.put(new JSONObject().put("url", url).put("manifest", manifest));
        prefs.edit().putString("items", next.toString()).apply();
    }

    public synchronized void remove(String id) throws JSONException {
        JSONArray items = installed(), next = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (!id.equals(item.getJSONObject("manifest").optString("id"))) next.put(item);
        }
        prefs.edit().putString("items", next.toString()).apply();
    }

    public static final class Results {
        public final List<JSONObject> items = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
    }

    public List<JSONObject> catalogs(String type, boolean search) throws Exception {
        List<JSONObject> list=new ArrayList<>(); JSONArray all=installed();
        for(int i=0;i<all.length();i++) {
            JSONObject addon=all.getJSONObject(i), manifest=addon.getJSONObject("manifest");
            if(!Protocol.resource(manifest,"catalog",type,null))continue;
            JSONArray cs=manifest.optJSONArray("catalogs");if(cs==null)continue;
            for(int j=0;j<cs.length();j++) {
                JSONObject c=cs.getJSONObject(j);
                if(!type.equals(c.optString("type")) || (search?!Protocol.searchable(c):!browsableWithOptions(c)))continue;
                list.add(new JSONObject(c.toString()).put("addonUrl",addon.getString("url"))
                    .put("label",manifest.optString("name")+" · "+c.optString("name",c.optString("id"))));
            }
        }
        return list;
    }
    private static boolean browsableWithOptions(JSONObject c){
        if(Protocol.browsable(c))return true;
        JSONArray es=c.optJSONArray("extra");if(es==null)return false;
        for(int i=0;i<es.length();i++){JSONObject e=es.optJSONObject(i);if(e!=null&&e.optBoolean("isRequired")&&(!"genre".equals(e.optString("name"))||e.optJSONArray("options")==null||e.optJSONArray("options").length()==0))return false;}
        return true;
    }
    public static String defaultGenre(JSONObject c){
        JSONArray es=c.optJSONArray("extra");if(es!=null)for(int i=0;i<es.length();i++){JSONObject e=es.optJSONObject(i);if(e!=null&&"genre".equals(e.optString("name"))&&e.optBoolean("isRequired")){JSONArray opts=e.optJSONArray("options");if(opts!=null)return opts.optString(0);}}
        return "";
    }
    public Results catalog(JSONObject catalog,String type,String query,int skip,String genre) throws Exception {
        Results result=new Results();
        String url=Protocol.base(catalog.getString("addonUrl"))+"/catalog/"+Protocol.encode(type)+"/"+Protocol.encode(catalog.getString("id"));
        List<String> extras=new ArrayList<>();
        if(!query.isEmpty())extras.add("search="+Protocol.encode(query));
        if(skip>0)extras.add("skip="+skip);
        if(!genre.isEmpty())extras.add("genre="+Protocol.encode(genre));
        url+=(extras.isEmpty()?"":"/"+String.join("&",extras))+".json";
        JSONArray metas=get(url).optJSONArray("metas");
        if(metas!=null)for(int i=0;i<metas.length();i++) {
            JSONObject m=metas.getJSONObject(i);if(m.optString("id").isEmpty())continue;
            if(m.optString("type").isEmpty())m.put("type",type);result.items.add(m);
        }
        return result;
    }
    public static boolean supports(JSONObject c,String key) {
        JSONArray es=c.optJSONArray("extra");if(es!=null)for(int i=0;i<es.length();i++)
            if(es.optJSONObject(i)!=null&&key.equals(es.optJSONObject(i).optString("name")))return true;
        JSONArray old=c.optJSONArray("extraSupported");if(old!=null)for(int i=0;i<old.length();i++)if(key.equals(old.optString(i)))return true;
        return false;
    }

    public JSONObject meta(String type, String id) throws Exception {
        JSONArray addons = installed();
        Exception last = null;
        for (int i = 0; i < addons.length(); i++) {
            JSONObject addon = addons.getJSONObject(i), manifest = addon.getJSONObject("manifest");
            if (!Protocol.resource(manifest, "meta", type, id)) continue;
            try {
                JSONObject meta = get(Protocol.base(addon.getString("url")) + "/meta/" + Protocol.encode(type) + "/" + Protocol.encode(id) + ".json")
                        .optJSONObject("meta");
                if (meta != null) return meta;
            } catch (Exception e) { last = e; }
        }
        if (last != null) throw last;
        throw new IOException("No installed addon can provide details for this title.");
    }

    public Results streams(String type, String id) throws Exception {
        Results result = new Results();
        JSONArray addons = installed();
        int requests = 0;
        for (int i = 0; i < addons.length(); i++) {
            JSONObject addon = addons.getJSONObject(i), manifest = addon.getJSONObject("manifest");
            if (!Protocol.resource(manifest, "stream", type, id)) continue;
            requests++;
            try {
                JSONObject response = get(Protocol.base(addon.getString("url")) + "/stream/" + Protocol.encode(type) + "/" + Protocol.encode(id) + ".json");
                JSONArray streams = response.optJSONArray("streams");
                if (streams != null) for (int j = 0; j < streams.length(); j++)
                    result.items.add(streams.getJSONObject(j).put("addonName", manifest.optString("name")));
            } catch (Exception e) {
                result.errors.add(manifest.optString("name") + ": source lookup failed. Retry or check its configuration.");
            }
        }
        if (requests == 0) result.errors.add("Install Torrentio or another " + ("series".equals(type) ? "TV" : "movie") + " stream addon first.");
        return result;
    }

    public byte[] image(String url) throws Exception {
        URI uri = URI.create(url == null ? "" : url.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IOException("Unsupported artwork URL.");
        try (Response response = http.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("Artwork returned HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty artwork.");
            long length = body.contentLength();
            if (length > 6 * 1024 * 1024L) throw new IOException("Artwork too large.");
            byte[] bytes = body.bytes();
            if (bytes.length > 6 * 1024 * 1024) throw new IOException("Artwork too large.");
            return bytes;
        }
    }

    private JSONObject get(String url) throws Exception {
        try (Response response = http.newCall(new Request.Builder().url(url).header("Accept", "application/json").build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("Addon returned HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty addon response.");
            if (body.contentLength() > 4 * 1024 * 1024) throw new IOException("Addon response too large.");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            java.io.InputStream input = body.byteStream();
            while ((count = input.read(buffer)) != -1) {
                if (out.size() + count > 4 * 1024 * 1024) throw new IOException("Addon response too large.");
                out.write(buffer, 0, count);
            }
            return new JSONObject(out.toString("UTF-8"));
        }
    }
}
