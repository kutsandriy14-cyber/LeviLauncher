package org.levimc.launcher.ui.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import org.levimc.launcher.R;
import org.levimc.launcher.ui.animation.DynamicAnim;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AboutActivity extends BaseActivity {

    private static final String AVATAR_URL = "https://github.com/kutsandriy14-cyber.png";
    private static final String URL_REPO = "https://github.com/kutsandriy14-cyber/LeviLauncher";
    private static final String URL_ORG = "https://github.com/kutsandriy14-cyber";
    private static final String URL_ISSUES = "https://github.com/kutsandriy14-cyber/LeviLauncher/issues";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        setupNavBar();

        loadAvatars();
        setupLinks();
        styleBadges();

        DynamicAnim.applyPressScaleRecursively(findViewById(android.R.id.content));
    }

    private void styleBadges() {
        TextView authorBadge = findViewById(R.id.author_badge);
        TextView maintainerBadge = findViewById(R.id.maintainer_badge);

        org.levimc.launcher.util.PersonalizationManager pm = new org.levimc.launcher.util.PersonalizationManager(this);
        int accent = pm.getAccentColor();
        if (accent != 0) {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            gd.setColor(accent);
            gd.setCornerRadius(4 * getResources().getDisplayMetrics().density);
            if (authorBadge != null) {
                authorBadge.setBackground(gd);
                authorBadge.setTextColor(android.graphics.Color.WHITE);
            }
            if (maintainerBadge != null) {
                maintainerBadge.setBackground(gd);
                maintainerBadge.setTextColor(android.graphics.Color.WHITE);
            }
        }
    }

    private void setupNavBar() {
        setActiveNavTab(R.id.nav_tab_about);
        findViewById(R.id.nav_tab_about).setOnClickListener(v -> {});
    }

    private void loadAvatars() {
        com.microsoft.xbox.idp.toolkit.CircleImageView avatar = findViewById(R.id.author_avatar);

        executor.execute(() -> {
            try {
                if (avatar != null) {
                    Response resp = client.newCall(new Request.Builder().url(AVATAR_URL).build()).execute();
                    if (resp.isSuccessful() && resp.body() != null) {
                        Bitmap bmp = BitmapFactory.decodeStream(resp.body().byteStream());
                        runOnUiThread(() -> {
                            if (bmp != null) avatar.setImageBitmap(bmp);
                        });
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private void setupLinks() {
        setupLinkButton(R.id.btn_github_repo, URL_REPO);
        setupLinkButton(R.id.btn_github_org, URL_ORG);
        setupLinkButton(R.id.btn_issues, URL_ISSUES);
        setupLinkButton(R.id.btn_star_fork, URL_REPO);
    }

    private void setupLinkButton(int viewId, String url) {
        TextView btn = findViewById(viewId);
        if (btn == null) return;
        btn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {}
        });
        DynamicAnim.applyPressScale(btn);
    }
}
