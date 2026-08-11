package org.levimc.launcher.ui.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.levimc.launcher.R;
import org.levimc.launcher.ui.animation.DynamicAnim;
import org.levimc.launcher.util.DownloadHistoryStore;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class DownloadHistoryActivity extends BaseActivity {
    private LinearLayout list;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_history);
        list = findViewById(R.id.download_history_list);
        Button clear = findViewById(R.id.download_history_clear);
        clear.setOnClickListener(v -> {
            DownloadHistoryStore.clear(this);
            render();
        });
        DynamicAnim.applyPressScale(clear);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null) render();
    }

    private void render() {
        list.removeAllViews();
        List<DownloadHistoryStore.Entry> entries = DownloadHistoryStore.get(this);
        if (entries.isEmpty()) {
            TextView empty = createText(getString(R.string.no_history), 14, getColor(R.color.text_secondary));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(8, dp(30), 8, dp(30));
            list.addView(empty);
            return;
        }
        for (DownloadHistoryStore.Entry entry : entries) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(8), dp(9), dp(8), dp(9));
            TextView title = createText((entry.version.isEmpty() ? "Minecraft" : entry.version)
                    + " · " + entry.action + " · " + entry.status, 14, getColor(R.color.on_surface));
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            TextView date = createText(DateFormat.getDateTimeInstance().format(new Date(entry.timestamp)), 11, getColor(R.color.text_secondary));
            row.addView(title);
            row.addView(date);
            if (!entry.detail.isEmpty()) row.addView(createText(entry.detail, 11, getColor(R.color.text_secondary)));
            list.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView divider = new TextView(this);
            divider.setBackgroundColor(Color.argb(28, 255, 255, 255));
            list.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
    }

    private TextView createText(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        return view;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
}
