package org.levimc.launcher.util;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.levimc.launcher.ui.adapter.OnlineVersionAdapter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the real APK assets published in the user-owned GitHub repository.
 * No version download URLs are hard-coded: an asset appears only when GitHub
 * currently reports it as an APK release asset.
 */
public final class GithubMinecraftCatalog {
    private static final String OWNER = "kutsandriy14-cyber";
    private static final String REPOSITORY = "Apk-download";
    private static final String RELEASES_URL = "https://api.github.com/repos/"
            + OWNER + "/" + REPOSITORY + "/releases?per_page=100";
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "minecraft[-_]?((?:1[-_.])?\\d{1,3}(?:[-_.]\\d{1,3}){2,3})",
            Pattern.CASE_INSENSITIVE);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onSuccess(List<OnlineVersionAdapter.OnlineVersion> versions);
        void onError(String message);
    }

    private GithubMinecraftCatalog() { }

    public static void fetch(Context context, Callback callback) {
        if (context != null && context.getSharedPreferences("launcher_options", Context.MODE_PRIVATE)
                .getBoolean("local_only_mode", false)) {
            callback.onError("Online catalog is disabled while Local-only mode is enabled.");
            return;
        }
        fetch(callback);
    }

    public static void fetch(Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                List<OnlineVersionAdapter.OnlineVersion> versions = fetchNow();
                callback.onSuccess(versions);
            } catch (Exception error) {
                callback.onError(error.getMessage() == null ? "Could not load APK catalog" : error.getMessage());
            }
        });
    }

    private static List<OnlineVersionAdapter.OnlineVersion> fetchNow() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "LeviLauncher");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("GitHub catalog returned HTTP " + status);
            }
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            }
            return parse(response.toString());
        } finally {
            connection.disconnect();
        }
    }

    static List<OnlineVersionAdapter.OnlineVersion> parse(String response) {
        JsonElement root = JsonParser.parseString(response);
        if (!root.isJsonArray()) return Collections.emptyList();
        Map<String, OnlineVersionAdapter.OnlineVersion> unique = new LinkedHashMap<>();
        JsonArray releases = root.getAsJsonArray();
        for (JsonElement releaseElement : releases) {
            if (!releaseElement.isJsonObject()) continue;
            JsonObject release = releaseElement.getAsJsonObject();
            if (readBoolean(release, "draft")) continue;
            boolean releaseBeta = readBoolean(release, "prerelease");
            JsonElement assetsElement = release.get("assets");
            if (assetsElement == null || !assetsElement.isJsonArray()) continue;
            for (JsonElement assetElement : assetsElement.getAsJsonArray()) {
                if (!assetElement.isJsonObject()) continue;
                JsonObject asset = assetElement.getAsJsonObject();
                String name = readString(asset, "name");
                String assetUrl = readString(asset, "browser_download_url");
                if (name == null || assetUrl == null || !name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                String version = versionFromAssetName(name);
                if (version == null || !isAtLeast116(version)) continue;
                boolean beta = releaseBeta || name.toLowerCase(Locale.ROOT).contains("beta")
                        || name.toLowerCase(Locale.ROOT).contains("preview");
                OnlineVersionAdapter.OnlineVersion item = new OnlineVersionAdapter.OnlineVersion(version, assetUrl, beta);
                item.sizeBytes = readLong(asset, "size");
                String key = version + ":" + beta;
                OnlineVersionAdapter.OnlineVersion old = unique.get(key);
                // Prefer the currently published complete asset when duplicate file names exist.
                if (old == null || item.sizeBytes > old.sizeBytes) unique.put(key, item);
            }
        }
        ArrayList<OnlineVersionAdapter.OnlineVersion> result = new ArrayList<>(unique.values());
        result.sort((left, right) -> compareVersions(right.version, left.version));
        return result;
    }

    static String versionFromAssetName(String assetName) {
        Matcher matcher = VERSION_PATTERN.matcher(assetName);
        if (!matcher.find()) return null;
        String[] raw = matcher.group(1).split("[-_.]");
        ArrayList<Integer> values = new ArrayList<>();
        for (String part : raw) {
            try { values.add(Integer.parseInt(part)); }
            catch (NumberFormatException ignored) { return null; }
        }
        if (values.size() < 3) return null;
        if (values.get(0) != 1) values.add(0, 1);
        if (values.size() < 4) return null;
        return values.get(0) + "." + values.get(1) + "." + values.get(2) + "." + values.get(3);
    }

    private static boolean readBoolean(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    private static String readString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static long readLong(JsonObject object, String key) {
        JsonElement value = object.get(key);
        try { return value == null || value.isJsonNull() ? -1L : value.getAsLong(); }
        catch (Exception ignored) { return -1L; }
    }

    private static boolean isAtLeast116(String version) {
        return compareVersions(version, "1.16.0") >= 0;
    }

    private static int compareVersions(String first, String second) {
        String[] left = first.split("\\.");
        String[] right = second.split("\\.");
        int count = Math.max(left.length, right.length);
        for (int index = 0; index < count; index++) {
            int leftPart = index < left.length ? safePart(left[index]) : 0;
            int rightPart = index < right.length ? safePart(right[index]) : 0;
            if (leftPart != rightPart) return Integer.compare(leftPart, rightPart);
        }
        return 0;
    }

    private static int safePart(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
