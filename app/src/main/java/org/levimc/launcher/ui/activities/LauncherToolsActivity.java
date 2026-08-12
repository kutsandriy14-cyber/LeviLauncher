package org.levimc.launcher.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import org.levimc.launcher.R;
import org.levimc.launcher.ui.animation.DynamicAnim;

/**
 * Central, visible entry point for launcher maintenance features.  Each action is
 * kept in a dedicated screen so users do not need to discover it through settings.
 */
public class LauncherToolsActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher_tools);

        bind(R.id.tools_online_install, InstancesActivity.class);
        bind(R.id.tools_backups, BackupCenterActivity.class);
        bind(R.id.tools_content, ContentManagementActivity.class);
        bind(R.id.tools_download_history, DownloadHistoryActivity.class);
        bind(R.id.tools_device_readiness, DeviceReadinessActivity.class);
        bind(R.id.tools_diagnostics, DiagnosticsActivity.class);
        bind(R.id.tools_mod_profiles, ModProfilesActivity.class);
        bind(R.id.tools_settings, SettingsActivity.class);
    }

    private void bind(int viewId, Class<?> destination) {
        View view = findViewById(viewId);
        if (view == null) return;
        view.setOnClickListener(v -> startActivity(new Intent(this, destination)));
        DynamicAnim.applyPressScale(view);
    }
}
