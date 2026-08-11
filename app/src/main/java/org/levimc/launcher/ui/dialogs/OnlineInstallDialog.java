package org.levimc.launcher.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.levimc.launcher.R;
import org.levimc.launcher.ui.adapter.OnlineVersionAdapter;
import org.levimc.launcher.util.ApkDownloadManager;
import org.levimc.launcher.util.PersonalizationManager;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Browse user-configured Minecraft APK sources with search, sort and source file sizes. */
public class OnlineInstallDialog extends Dialog {
    private final List<OnlineVersionAdapter.OnlineVersion> allVersions;
    private final List<OnlineVersionAdapter.OnlineVersion> displayVersions = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService metadataExecutor = Executors.newSingleThreadExecutor();
    private OnlineVersionAdapter adapter;
    private boolean showingBeta;
    private boolean newestFirst = true;
    private String query = "";

    public OnlineInstallDialog(@NonNull Context context, List<OnlineVersionAdapter.OnlineVersion> versions) {
        super(context);
        this.allVersions = versions;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_online_install);

        RecyclerView recycler = findViewById(R.id.online_versions_recycler);
        recycler.setLayoutManager(new GridLayoutManager(getContext(), 2));
        adapter = new OnlineVersionAdapter(displayVersions, version -> {
            ApkDownloadManager downloadManager = new ApkDownloadManager(getOwnerActivity());
            downloadManager.downloadAndInstall(version.url, "minecraft_" + version.version + ".apk");
            dismiss();
        });
        recycler.setAdapter(adapter);

        TextView tabRelease = findViewById(R.id.tab_release);
        TextView tabBeta = findViewById(R.id.tab_beta);
        TextView btnClose = findViewById(R.id.btn_close);
        EditText search = findViewById(R.id.online_version_search);
        Button sort = findViewById(R.id.online_version_sort);
        PersonalizationManager pm = new PersonalizationManager(getContext());
        int accent = pm.getAccentColor();

        tabRelease.setOnClickListener(v -> {
            showingBeta = false;
            updateTabs(tabRelease, tabBeta, accent);
            filterVersions();
        });
        tabBeta.setOnClickListener(v -> {
            showingBeta = true;
            updateTabs(tabRelease, tabBeta, accent);
            filterVersions();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                query = value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
                filterVersions();
            }
        });
        sort.setOnClickListener(v -> {
            newestFirst = !newestFirst;
            sort.setText(newestFirst ? "Newest" : "Oldest");
            filterVersions();
        });
        btnClose.setOnClickListener(v -> dismiss());

        updateTabs(tabRelease, tabBeta, accent);
        filterVersions();
        resolveSizesInBackground();

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            float density = getContext().getResources().getDisplayMetrics().density;
            int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
            int maxWidth = (int) (500 * density);
            params.width = Math.min((int) (screenWidth * 0.85f), maxWidth);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }
    }

    private void filterVersions() {
        if (adapter == null) return;
        displayVersions.clear();
        for (OnlineVersionAdapter.OnlineVersion version : allVersions) {
            if (version.isBeta != showingBeta) continue;
            if (!isAtLeast116(version.version)) continue;
            if (!query.isEmpty() && !version.version.toLowerCase(Locale.ROOT).contains(query)) continue;
            displayVersions.add(version);
        }
        displayVersions.sort((left, right) -> newestFirst
                ? compareVersions(right.version, left.version)
                : compareVersions(left.version, right.version));
        adapter.notifyDataSetChanged();
    }

    private void resolveSizesInBackground() {
        metadataExecutor.execute(() -> {
            for (OnlineVersionAdapter.OnlineVersion version : allVersions) {
                if (version.sizeBytes > 0L) continue;
                long size = resolveSize(version.url);
                if (size > 0L) {
                    version.sizeBytes = size;
                    mainHandler.post(() -> {
                        if (adapter != null) adapter.notifyDataSetChanged();
                    });
                }
            }
        });
    }

    private long resolveSize(String urlString) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.connect();
            return connection.getContentLengthLong();
        } catch (Exception ignored) {
            return -1L;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean isAtLeast116(String version) {
        String[] parts = version.split("\\.");
        if (parts.length < 2) return false;
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major > 1 || (major == 1 && minor >= 16);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static int compareVersions(String first, String second) {
        String[] left = first.split("\\.");
        String[] right = second.split("\\.");
        int length = Math.max(left.length, right.length);
        for (int index = 0; index < length; index++) {
            int leftPart = index < left.length ? safePart(left[index]) : 0;
            int rightPart = index < right.length ? safePart(right[index]) : 0;
            if (leftPart != rightPart) return Integer.compare(leftPart, rightPart);
        }
        return 0;
    }

    private static int safePart(String value) {
        try { return Integer.parseInt(value.replaceAll("\\D.*", "")); }
        catch (Exception ignored) { return 0; }
    }

    private void updateTabs(TextView release, TextView beta, int accent) {
        if (!showingBeta) {
            release.setBackgroundTintList(ColorStateList.valueOf(accent != 0 ? accent : Color.parseColor("#1B5E20")));
            release.setTextColor(Color.WHITE);
            beta.setBackgroundTintList(null);
            beta.setTextColor(Color.GRAY);
        } else {
            beta.setBackgroundTintList(ColorStateList.valueOf(accent != 0 ? accent : Color.parseColor("#1B5E20")));
            beta.setTextColor(Color.WHITE);
            release.setBackgroundTintList(null);
            release.setTextColor(Color.GRAY);
        }
    }

    @Override
    public void dismiss() {
        metadataExecutor.shutdownNow();
        super.dismiss();
    }
}
