package org.levimc.launcher.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.levimc.launcher.R;
import org.levimc.launcher.ui.adapter.OnlineVersionAdapter;
import org.levimc.launcher.util.ApkDownloadManager;
import org.levimc.launcher.util.PersonalizationManager;

import java.util.ArrayList;
import java.util.List;

public class OnlineInstallDialog extends Dialog {

    private final List<OnlineVersionAdapter.OnlineVersion> allVersions;
    private final List<OnlineVersionAdapter.OnlineVersion> displayVersions = new ArrayList<>();
    private OnlineVersionAdapter adapter;
    private boolean showingBeta = false;

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

        btnClose.setOnClickListener(v -> dismiss());

        updateTabs(tabRelease, tabBeta, accent);
        filterVersions();

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
        displayVersions.clear();
        for (OnlineVersionAdapter.OnlineVersion v : allVersions) {
            if (v.isBeta == showingBeta) {
                displayVersions.add(v);
            }
        }
        adapter.notifyDataSetChanged();
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
}
