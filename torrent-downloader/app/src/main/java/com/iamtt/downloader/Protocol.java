package com.iamtt.downloader;

import org.json.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.IOException;
import java.util.*;

/** Pure protocol and file-selection rules; no Android or native engine required. */
public final class Protocol {
    private Protocol() {}
    public static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
    }
    public static String manifestUrl(String input) {
        String value = input.trim().replaceFirst("(?i)^stremio://", "https://");
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null ||
                !uri.getPath().endsWith("/manifest.json"))
            throw new IllegalArgumentException("Paste the HTTPS addon link ending in /manifest.json.");
        return value;
    }
    public static String base(String manifest) {
        String checked = manifestUrl(manifest);
        return checked.substring(0, checked.length() - "/manifest.json".length());
    }
    public static boolean resource(JSONObject manifest, String name, String type, String id) {
        JSONArray types = manifest.optJSONArray("types");
        if (types != null && !contains(types, type)) return false;
        JSONArray resources = manifest.optJSONArray("resources");
        if (resources == null) return false;
        for (int i = 0; i < resources.length(); i++) {
            Object item = resources.opt(i);
            JSONObject object = item instanceof JSONObject ? (JSONObject)item : null;
            if (!name.equals(object == null ? String.valueOf(item) : object.optString("name"))) continue;
            JSONArray supported = object == null ? null : object.optJSONArray("types");
            JSONArray prefixes = object == null ? manifest.optJSONArray("idPrefixes") : object.optJSONArray("idPrefixes");
            if (supported != null && !contains(supported, type)) continue;
            if (prefixes != null && prefixes.length() > 0 && id != null) {
                boolean match = false;
                for (int j = 0; j < prefixes.length(); j++) if (id.startsWith(prefixes.optString(j))) match = true;
                if (!match) continue;
            }
            return true;
        }
        return false;
    }
    private static boolean contains(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) if (value.equals(array.optString(i))) return true;
        return false;
    }
    public static boolean searchable(JSONObject catalog) {
        JSONArray extra = catalog.optJSONArray("extra");
        if (extra != null) for (int i = 0; i < extra.length(); i++) {
            JSONObject e = extra.optJSONObject(i);
            if (e != null && "search".equals(e.optString("name"))) return true;
        }
        JSONArray supported = catalog.optJSONArray("extraSupported");
        return supported != null && contains(supported, "search");
    }
    public static boolean browsable(JSONObject catalog) {
        JSONArray extra = catalog.optJSONArray("extra");
        if (extra != null) for (int i = 0; i < extra.length(); i++) {
            JSONObject e = extra.optJSONObject(i);
            if (e != null && e.optBoolean("isRequired")) return false;
        }
        JSONArray required = catalog.optJSONArray("extraRequired");
        return required == null || required.length() == 0;
    }
    public static String magnet(JSONObject stream, String title) {
        String hash = stream.optString("infoHash").toLowerCase(Locale.ROOT);
        if (!hash.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("This source has no supported torrent hash.");
        StringBuilder result = new StringBuilder("magnet:?xt=urn:btih:").append(hash)
                .append("&dn=").append(encode(title));
        JSONArray sources = stream.optJSONArray("sources");
        if (sources != null) for (int i = 0; i < sources.length(); i++) {
            String tracker = sources.optString(i);
            if (tracker.startsWith("tracker:")) {
                tracker = tracker.substring(8);
                if (tracker.startsWith("udp://") || tracker.startsWith("https://") || tracker.startsWith("http://"))
                    result.append("&tr=").append(encode(tracker));
            }
        }
        return result.toString();
    }
    public static int fileIndex(JSONObject stream) {
        Object value = stream.opt("fileIdx");
        if (value == null || value == JSONObject.NULL) return -1;
        if (!(value instanceof Number) || ((Number)value).doubleValue() != ((Number)value).intValue() || ((Number)value).intValue() < 0)
            throw new IllegalArgumentException("Addon returned an invalid file index.");
        return ((Number)value).intValue();
    }
    public static int selectFile(int requested, String[] paths, long[] sizes) {
        if (requested >= paths.length) throw new IllegalArgumentException("Addon file index does not exist in this torrent.");
        if (requested >= 0) return requested;
        int selected = -1;
        long largest = -1;
        for (int i = 0; i < paths.length; i++) {
            if (paths[i].toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mkv|avi|mov|webm|m4v|ts|m2ts)") && sizes[i] > largest) {
                largest = sizes[i]; selected = i;
            }
        }
        if (selected < 0) throw new IllegalArgumentException("No video file was found in this torrent.");
        return selected;
    }
    public static File safeFile(File root, String path) throws IOException {
        File result = new File(root, path).getCanonicalFile();
        if (new File(path).isAbsolute() || !result.getPath().startsWith(root.getCanonicalPath() + File.separator))
            throw new IOException("Unsafe torrent file path.");
        return result;
    }
}
