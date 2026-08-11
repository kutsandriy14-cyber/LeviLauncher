package org.levimc.launcher.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stores a small local-only audit trail for user-visible download and install outcomes. */
public final class DownloadHistoryStore {
    private static final String PREFS = "download_history";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 80;
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<ArrayList<Entry>>() {}.getType();

    private DownloadHistoryStore() {}

    public static synchronized void add(Context context, String version, String action, String status, String detail) {
        ArrayList<Entry> items = new ArrayList<>(get(context));
        items.add(0, new Entry(System.currentTimeMillis(), safe(version), safe(action), safe(status), safe(detail)));
        while (items.size() > MAX_ITEMS) items.remove(items.size() - 1);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_ITEMS, GSON.toJson(items)).apply();
    }

    public static synchronized List<Entry> get(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = preferences.getString(KEY_ITEMS, "");
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            List<Entry> items = GSON.fromJson(json, LIST_TYPE);
            return items == null ? Collections.emptyList() : items;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    public static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ITEMS).apply();
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public static final class Entry {
        public long timestamp;
        public String version;
        public String action;
        public String status;
        public String detail;

        public Entry(long timestamp, String version, String action, String status, String detail) {
            this.timestamp = timestamp;
            this.version = version;
            this.action = action;
            this.status = status;
            this.detail = detail;
        }
    }
}
