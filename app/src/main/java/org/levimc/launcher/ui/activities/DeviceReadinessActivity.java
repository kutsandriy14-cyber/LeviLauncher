package org.levimc.launcher.ui.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.levimc.launcher.R;
import org.levimc.launcher.ui.animation.DynamicAnim;
import org.levimc.launcher.util.LauncherTaskQueue;

import java.io.File;
import java.util.Locale;

/**
 * A non-destructive preflight page for the launcher.  It surfaces conditions that can
 * prevent a download or APK install before the user starts a long operation.
 */
public class DeviceReadinessActivity extends BaseActivity {
    private static final String PREFS = "launcher_options";
    private static final String KEY_LOCAL_ONLY = "local_only_mode";

    private TextView storageView;
    private TextView permissionsView;
    private TextView queueView;
    private SwitchMaterial localOnlySwitch;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_readiness);

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        storageView = findViewById(R.id.readiness_storage);
        permissionsView = findViewById(R.id.readiness_permissions);
        queueView = findViewById(R.id.readiness_queue);
        localOnlySwitch = findViewById(R.id.switch_local_only);

        Button refresh = findViewById(R.id.btn_refresh_readiness);
        Button openPermissions = findViewById(R.id.btn_open_permissions);
        refresh.setOnClickListener(v -> refreshReadiness());
        openPermissions.setOnClickListener(v -> openApplicationSettings());
        DynamicAnim.applyPressScale(refresh);
        DynamicAnim.applyPressScale(openPermissions);

        localOnlySwitch.setChecked(preferences.getBoolean(KEY_LOCAL_ONLY, false));
        localOnlySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_LOCAL_ONLY, isChecked).apply();
            refreshReadiness();
        });
        refreshReadiness();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshReadiness();
    }

    private void refreshReadiness() {
        File internal = Environment.getDataDirectory();
        File appExternal = getExternalFilesDir(null);
        long internalFree = internal == null ? 0L : internal.getUsableSpace();
        long externalFree = appExternal == null ? 0L : appExternal.getUsableSpace();
        storageView.setText("Storage preflight\n\nInternal free: " + formatBytes(internalFree)
                + "\nLauncher storage free: " + formatBytes(externalFree)
                + "\n\nKeep enough free storage for the APK, extraction, and a backup before updating Minecraft.");

        boolean storageAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
        boolean notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean installPackages = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || getPackageManager().canRequestPackageInstalls();
        permissionsView.setText("Permission guidance\n\nStorage access: " + state(storageAccess)
                + "\nInstall unknown apps: " + state(installPackages)
                + "\nNotifications: " + state(notifications)
                + "\n\nUse App permissions below to review access. Android may show its separate unknown-apps screen when an APK install is requested.");

        int pending = LauncherTaskQueue.getPendingCount();
        boolean localOnly = preferences.getBoolean(KEY_LOCAL_ONLY, false);
        queueView.setText("Task queue\n\nOperations waiting or running: " + pending
                + "\nNetwork mode: " + (localOnly ? "Local-only" : "Online catalog enabled")
                + "\n\nDownloads, installations and clone operations run one at a time to avoid storage conflicts.");
        if (localOnlySwitch != null && localOnlySwitch.isChecked() != localOnly) {
            localOnlySwitch.setChecked(localOnly);
        }
    }

    private void openApplicationSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private static String state(boolean granted) {
        return granted ? "Ready" : "Action needed";
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0L) return "Unavailable";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int index = 0;
        while (value >= 1024D && index < units.length - 1) {
            value /= 1024D;
            index++;
        }
        return String.format(Locale.getDefault(), value >= 100D ? "%.0f %s" : "%.1f %s", value, units[index]);
    }
}
