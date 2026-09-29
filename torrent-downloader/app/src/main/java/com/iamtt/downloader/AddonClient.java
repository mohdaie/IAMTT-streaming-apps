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

    public Results catalog(String type, String query) throws Exception {
        JSONArray installed = installed();
        Results result = new Results();
        Set<String> seen = new HashSet<>();
        int requests = 0;
        for (int i = 0; i < installed.length(); i++) {
            JSONObject addon = installed.getJSONObject(i), manifest = addon.getJSONObject("manifest");
            if (!Protocol.resource(manifest, "catalog", type, null)) continue;
            JSONArray catalogs = manifest.optJSONArray("catalogs");
            if (catalogs == null) continue;
            for (int j = 0; j < catalogs.length() && requests < 8; j++) {
                JSONObject catalog = catalogs.getJSONObject(j);
                if (!type.equals(catalog.optString("type"))) continue;
                if (query.isEmpty() ? !Protocol.browsable(catalog) : !Protocol.searchable(catalog)) continue;
                requests++;
                try {
                    String url = Protocol.base(addon.getString("url")) + "/catalog/" + Protocol.encode(type) + "/" + Protocol.encode(catalog.getString("id"));
                    url += query.isEmpty() ? ".json" : "/search=" + Protocol.encode(query) + ".json";
                    JSONArray metas = get(url).optJSONArray("metas");
                    if (metas != null) for (int k = 0; k < metas.length() && result.items.size() < 120; k++) {
                        JSONObject media = metas.getJSONObject(k);
                        String id = media.optString("id");
                        if (!id.isEmpty() && seen.add(type + ":" + id)) {
                            if (media.optString("type").isEmpty()) media.put("type", type);
                            result.items.add(media);
                        }
                    }
                } catch (Exception e) {
                    result.errors.add(manifest.optString("name") + ": catalogue unavailable. Try again.");
                }
            }
        }
        if (requests == 0) result.errors.add("Install a " + ("series".equals(type) ? "TV" : "movie") + " catalogue addon to browse and search.");
        return result;
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
