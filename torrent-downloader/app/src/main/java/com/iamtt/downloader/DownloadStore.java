package com.iamtt.downloader;

import android.content.Context;
import org.json.*;
import java.util.*;

public final class DownloadStore {
    public static final class Item {
        public final String id, hash, title, magnet;
        public final int requested;
        public volatile String state, detail, path = "";
        public volatile long done, total, expectedTotal, speed;
        public volatile int peers, reportedSeeds=-1;
        public volatile String sourcePublisher="", quality="", codec="";

        Item(String hash, String title, String magnet, int requested, String state) {
            this.hash=hash; this.title=title; this.magnet=magnet; this.requested=requested;
            this.id=hash+":"+requested; this.state=state; detail="";
        }
    }

    private static DownloadStore instance;
    public static synchronized DownloadStore get(Context c) {
        if (instance == null) instance = new DownloadStore(c.getApplicationContext());
        return instance;
    }

    private final android.content.SharedPreferences prefs;
    private final LinkedHashMap<String,Item> items = new LinkedHashMap<>();

    private DownloadStore(Context c) {
        prefs = c.getSharedPreferences("downloads", Context.MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(prefs.getString("items", "[]"));
            for (int i=0;i<a.length();i++) {
                JSONObject j=a.getJSONObject(i);
                Item item=new Item(j.getString("hash"),j.getString("title"),j.getString("magnet"),j.getInt("requested"),
                    "Complete".equals(j.optString("state")) ? "Complete" : "Paused");
                item.path=j.optString("path"); item.done=j.optLong("done"); item.total=j.optLong("total");
                item.expectedTotal=j.optLong("expectedTotal"); item.reportedSeeds=j.optInt("reportedSeeds",-1);
                item.sourcePublisher=j.optString("sourcePublisher"); item.quality=j.optString("quality"); item.codec=j.optString("codec");
                items.put(item.id,item);
            }
        } catch (JSONException ignored) { /* Invalid stored jobs are never auto-started. */ }
    }

    public synchronized List<Item> all() { return new ArrayList<>(items.values()); }
    public synchronized Item find(String id) { return items.get(id); }

    public synchronized Item add(JSONObject stream,String title) throws Exception {
        String magnet=Protocol.magnet(stream,title), hash=stream.getString("infoHash").toLowerCase(Locale.ROOT);
        int requested=Protocol.fileIndex(stream);
        String id=hash+":"+requested;
        if (items.containsKey(id)) throw new IllegalArgumentException("This file is already in Downloads.");
        Item item=new Item(hash,title,magnet,requested,"Queued");
        item.expectedTotal=Protocol.sourceSize(stream);
        item.reportedSeeds=Protocol.reportedSeeders(stream);
        item.sourcePublisher=Protocol.sourcePublisher(stream);
        item.quality=Protocol.sourceQuality(stream);
        item.codec=Protocol.sourceCodec(stream);
        items.put(item.id,item); save(); return item;
    }

    public synchronized void save() {
        JSONArray a=new JSONArray();
        for(Item i:items.values()) try {
            a.put(new JSONObject().put("hash",i.hash).put("title",i.title).put("magnet",i.magnet)
                .put("requested",i.requested).put("state",i.state).put("path",i.path).put("done",i.done).put("total",i.total)
                .put("expectedTotal",i.expectedTotal).put("reportedSeeds",i.reportedSeeds)
                .put("sourcePublisher",i.sourcePublisher).put("quality",i.quality).put("codec",i.codec));
        } catch(JSONException ignored) { }
        prefs.edit().putString("items",a.toString()).apply();
    }

    public synchronized void pauseAll() {
        for(Item i:items.values()) if (!i.state.equals("Complete")) {i.state="Paused";i.speed=0;}
        save();
    }

    public boolean wifiOnly() { return prefs.getBoolean("wifiOnly",true); }
    public void wifiOnly(boolean value) { prefs.edit().putBoolean("wifiOnly",value).apply(); }
}
