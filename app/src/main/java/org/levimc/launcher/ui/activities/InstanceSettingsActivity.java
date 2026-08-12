package org.levimc.launcher.ui.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.levimc.launcher.R;
import org.levimc.launcher.core.versions.GameVersion;
import org.levimc.launcher.core.versions.VersionManager;
import org.levimc.launcher.ui.animation.DynamicAnim;
import org.levimc.launcher.ui.dialogs.CustomAlertDialog;
import org.levimc.launcher.ui.dialogs.InstallProgressDialog;
import org.levimc.launcher.util.InstanceBackupManager;

public class InstanceSettingsActivity extends BaseActivity {
    private static final int REQUEST_BACKUP_STORAGE = 4201;

    private GameVersion version;
    private VersionManager versionManager;
    private InstanceBackupManager backupManager;
    private InstallProgressDialog backupProgressDialog;
    private Button backupButton;

    private TextView tabGeneral, tabLaunchOptions, tabManagement;
    private View sectionGeneral, sectionLaunchOptions, sectionManagement;

    private EditText editName;
    private SwitchMaterial switchIsolation;
    private SwitchMaterial switchLaunchVertically;
    private String originalDisplayName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_instance_settings);

        DynamicAnim.applyPressScaleRecursively(findViewById(android.R.id.content));

        setupNavBar();

        versionManager = VersionManager.get(this);
        backupManager = new InstanceBackupManager(this);

        version = getIntent().getParcelableExtra("version");
        if (version == null) {
            finish();
            return;
        }

        initViews();
        populateData();
        selectTab(tabGeneral);
    }

    private void initViews() {
        tabGeneral = findViewById(R.id.tab_general);
        tabLaunchOptions = findViewById(R.id.tab_launch_options);
        tabManagement = findViewById(R.id.tab_management);

        sectionGeneral = findViewById(R.id.section_general);
        sectionLaunchOptions = findViewById(R.id.section_launch_options);
        sectionManagement = findViewById(R.id.section_management);

        editName = findViewById(R.id.edit_instance_name);
        switchIsolation = findViewById(R.id.switch_version_isolation);
        switchLaunchVertically = findViewById(R.id.switch_launch_vertically);

        tabGeneral.setOnClickListener(v -> selectTab(tabGeneral));
        tabLaunchOptions.setOnClickListener(v -> selectTab(tabLaunchOptions));
        tabManagement.setOnClickListener(v -> selectTab(tabManagement));

        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.btn_ok).setOnClickListener(v -> saveAndFinish());

        Button btnDelete = findViewById(R.id.btn_delete_instance);
        backupButton = findViewById(R.id.btn_backup_instance);
        if (backupButton != null) {
            backupButton.setOnClickListener(v -> confirmBackup());
        }
        Button cloneButton = findViewById(R.id.btn_clone_instance);
        SwitchMaterial testSwitch = findViewById(R.id.switch_test_instance);
        Button historyButton = findViewById(R.id.btn_instance_history);
        if (cloneButton != null) {
            if (version.isInstalled) {
                cloneButton.setEnabled(false);
                cloneButton.setAlpha(0.4f);
            } else {
                cloneButton.setOnClickListener(v -> confirmClone());
            }
        }
        if (testSwitch != null) {
            testSwitch.setChecked(org.levimc.launcher.util.InstanceFeatureManager.isTestInstance(this, version));
            testSwitch.setOnCheckedChangeListener((button, checked) ->
                    org.levimc.launcher.util.InstanceFeatureManager.setTestLabel(this, version, checked));
        }
        if (historyButton != null) historyButton.setOnClickListener(v -> showChangeHistory());
        if (version.isInstalled) {
            btnDelete.setEnabled(false);
            btnDelete.setAlpha(0.4f);
        } else {
            btnDelete.setOnClickListener(v -> confirmDelete());
        }
    }

    private void populateData() {
        TextView instanceInfo = findViewById(R.id.instance_info);
        String type = version.isInstalled ? getString(R.string.tag_installed) : getString(R.string.tag_custom);
        String info = "Game Version: " + (version.versionCode != null ? version.versionCode : "—")
                + " · Name: " + (version.directoryName != null ? version.directoryName : "—")
                + " · " + type;
        instanceInfo.setText(info);

        String currentName = version.versionCode != null ? version.versionCode : "";
        if (version.displayName != null && !version.displayName.isEmpty()) {
            String dn = version.displayName;
            int parenIdx = dn.lastIndexOf(" (");
            if (parenIdx > 0) {
                currentName = dn.substring(0, parenIdx);
            } else {
                currentName = dn;
            }
        }
        originalDisplayName = currentName.trim();
        editName.setText(currentName);

        switchIsolation.setChecked(version.versionIsolation);
        switchLaunchVertically.setChecked(version.launchVertically);
    }

    private void selectTab(TextView selectedTab) {
        TextView[] tabs = {tabGeneral, tabLaunchOptions, tabManagement};
        View[] sections = {sectionGeneral, sectionLaunchOptions, sectionManagement};

        org.levimc.launcher.util.PersonalizationManager pm = new org.levimc.launcher.util.PersonalizationManager(this);
        int accent = pm.getAccentColor();

        for (int i = 0; i < tabs.length; i++) {
            boolean isSelected = tabs[i] == selectedTab;

            if (isSelected) {
                if (accent != 0) {
                    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                    gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    gd.setColor(accent);
                    gd.setCornerRadius(16 * getResources().getDisplayMetrics().density);
                    tabs[i].setBackground(gd);
                } else {
                    tabs[i].setBackgroundResource(R.drawable.bg_tab_selected);
                }
                tabs[i].setTextColor(android.graphics.Color.WHITE);
            } else {
                tabs[i].setBackgroundResource(R.drawable.bg_tab_unselected);
                tabs[i].setTextColor(getColor(R.color.text_secondary));
            }

            if (isSelected) {
                sections[i].setVisibility(View.VISIBLE);
                sections[i].setAlpha(0f);
                sections[i].animate().alpha(1f).setDuration(200).start();
            } else {
                sections[i].setVisibility(View.GONE);
            }
        }
    }

    private void saveAndFinish() {
        String newName = editName.getText().toString().trim();

        if (!newName.isEmpty() && !version.isInstalled && !newName.equals(originalDisplayName)) {
            versionManager.renameCustomVersion(version, newName, new VersionManager.OnRenameVersionCallback() {
                @Override
                public void onRenameCompleted(boolean success) {}

                @Override
                public void onRenameFailed(Exception e) {
                    runOnUiThread(() -> Toast.makeText(InstanceSettingsActivity.this,
                            "Rename failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        }

        versionManager.setInstanceVersionIsolation(version, switchIsolation.isChecked());
        versionManager.setInstanceLaunchVertically(version, switchLaunchVertically.isChecked());
        org.levimc.launcher.util.InstanceFeatureManager.addHistory(this, version.directoryName,
                "Instance settings saved", "Isolation=" + switchIsolation.isChecked() + ", vertical=" + switchLaunchVertically.isChecked());

        setResult(RESULT_OK);
        finish();
    }

    private void confirmClone() {
        android.widget.CheckBox copyData = new android.widget.CheckBox(this);
        int padding = (int) (18 * getResources().getDisplayMetrics().density);
        copyData.setPadding(padding, 0, padding, 0);
        copyData.setText("Copy worlds, settings, resource packs and mods");
        copyData.setChecked(true);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Clone instance")
                .setMessage("Creates a separate custom Minecraft instance. The original instance is not changed.")
                .setView(copyData)
                .setPositiveButton("Clone", (dialog, which) -> {
                    Toast.makeText(this, "Clone added to task queue…", Toast.LENGTH_SHORT).show();
                    org.levimc.launcher.util.InstanceFeatureManager.cloneInstance(this, version, copyData.isChecked(),
                            new org.levimc.launcher.util.InstanceFeatureManager.Callback() {
                                @Override public void onSuccess(String message) { runOnUiThread(() -> {
                                    Toast.makeText(InstanceSettingsActivity.this, message, Toast.LENGTH_LONG).show();
                                    setResult(RESULT_OK);
                                }); }
                                @Override public void onError(String message) { runOnUiThread(() ->
                                        Toast.makeText(InstanceSettingsActivity.this, message, Toast.LENGTH_LONG).show()); }
                            });
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showChangeHistory() {
        java.util.List<String> entries = org.levimc.launcher.util.InstanceFeatureManager
                .getRecentHistory(this, version.directoryName, 30);
        String message = entries.isEmpty() ? "No recorded changes yet." : android.text.TextUtils.join("\n\n", entries);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Instance change history")
                .setMessage(message)
                .setPositiveButton(getString(R.string.confirm), null)
                .show();
    }

    private void confirmDelete() {
        android.widget.LinearLayout choices = new android.widget.LinearLayout(this);
        choices.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (18 * getResources().getDisplayMetrics().density);
        choices.setPadding(padding, 0, padding, 0);
        android.widget.CheckBox keepData = new android.widget.CheckBox(this);
        keepData.setText("Keep worlds, settings and resource packs in a backup folder");
        keepData.setChecked(true);
        android.widget.CheckBox keepMods = new android.widget.CheckBox(this);
        keepMods.setText("Keep mods in a backup folder");
        keepMods.setChecked(true);
        choices.addView(keepData);
        choices.addView(keepMods);

        new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.instance_delete_confirm_title))
                .setMessage(getString(R.string.instance_delete_confirm_msg))
                .setView(choices)
                .setPositiveButton(getString(R.string.delete), (dialog, which) ->
                        preserveThenDelete(keepData.isChecked(), keepMods.isChecked()))
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void preserveThenDelete(boolean keepData, boolean keepMods) {
        if (!keepData && !keepMods) {
            deleteInstanceNow();
            return;
        }
        Toast.makeText(this, "Preparing selected data backup…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String profileId = version.directoryName == null ? "default" : version.directoryName;
                java.io.File root = new java.io.File(org.levimc.launcher.util.LauncherStorage.getBackupsRoot(this),
                        "preserved_" + profileId + "_" + System.currentTimeMillis());
                if (keepData) {
                    copyTree(org.levimc.launcher.util.LauncherStorage.getProfileGameDataDir(this, profileId, true),
                            new java.io.File(root, "game_data"));
                    copyTree(org.levimc.launcher.util.LauncherStorage.getProfileGameDataDir(this, profileId, false),
                            new java.io.File(root, "game_data_internal"));
                }
                if (keepMods) {
                    copyTree(org.levimc.launcher.util.LauncherStorage.getProfileModsDir(this, profileId),
                            new java.io.File(root, "mods"));
                }
                runOnUiThread(this::deleteInstanceNow);
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Could not preserve selected data: " + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "preserve-instance-data").start();
    }

    private void deleteInstanceNow() {
        versionManager.deleteCustomVersion(version, new VersionManager.OnDeleteVersionCallback() {
            @Override public void onDeleteCompleted(boolean success) {
                runOnUiThread(() -> {
                    setResult(RESULT_OK);
                    finish();
                });
            }
            @Override public void onDeleteFailed(Exception error) {
                runOnUiThread(() -> Toast.makeText(InstanceSettingsActivity.this,
                        "Delete failed: " + error.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private static void copyTree(java.io.File source, java.io.File target) throws java.io.IOException {
        if (source == null || !source.exists()) return;
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) throw new java.io.IOException("Cannot create " + target);
            java.io.File[] children = source.listFiles();
            if (children == null) return;
            for (java.io.File child : children) copyTree(child, new java.io.File(target, child.getName()));
            return;
        }
        java.io.File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new java.io.IOException("Cannot create " + parent);
        try (java.io.InputStream input = new java.io.FileInputStream(source);
             java.io.OutputStream output = new java.io.FileOutputStream(target)) {
            byte[] buffer = new byte[131072];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private void confirmBackup() {
        new CustomAlertDialog(this)
                .setTitleText(getString(R.string.instance_backup_title))
                .setMessage(getString(R.string.instance_backup_confirm_message))
                .setPositiveButton(getString(R.string.backup), v -> startBackupWithPermissionCheck())
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void startBackupWithPermissionCheck() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_BACKUP_STORAGE);
            return;
        }
        startBackup();
    }

    private void startBackup() {
        if (backupButton != null) {
            backupButton.setEnabled(false);
            backupButton.setAlpha(0.55f);
        }
        backupProgressDialog = new InstallProgressDialog(this);
        backupProgressDialog.setTitleText(getString(R.string.instance_backup_title));
        backupProgressDialog.setStatusText(getString(R.string.instance_backup_in_progress));
        backupProgressDialog.setProgress(0);
        backupProgressDialog.show();

        backupManager.backup(version, new InstanceBackupManager.BackupCallback() {
            @Override
            public void onStarted() {
                if (backupProgressDialog != null) {
                    backupProgressDialog.setProgress(0);
                    backupProgressDialog.setStatusText(getString(R.string.instance_backup_in_progress));
                }
            }

            @Override
            public void onProgress(int progress) {
                if (backupProgressDialog != null) {
                    backupProgressDialog.setProgress(progress);
                }
            }

            @Override
            public void onSuccess(String displayPath) {
                finishBackupProgress();
                new CustomAlertDialog(InstanceSettingsActivity.this)
                        .setTitleText(getString(R.string.instance_backup_success_title))
                        .setMessage(getString(R.string.instance_backup_success_message, displayPath))
                        .setPositiveButton(getString(R.string.confirm), null)
                        .show();
            }

            @Override
            public void onError(String message) {
                finishBackupProgress();
                new CustomAlertDialog(InstanceSettingsActivity.this)
                        .setTitleText(getString(R.string.instance_backup_failed_title))
                        .setMessage(getString(R.string.instance_backup_failed_message, message))
                        .setPositiveButton(getString(R.string.confirm), null)
                        .show();
            }
        });
    }

    private void finishBackupProgress() {
        if (backupProgressDialog != null && backupProgressDialog.isShowing()) {
            backupProgressDialog.dismiss();
        }
        backupProgressDialog = null;
        if (backupButton != null) {
            backupButton.setEnabled(true);
            backupButton.setAlpha(1f);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BACKUP_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBackup();
            } else {
                Toast.makeText(this, R.string.storage_permission_not_granted, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupNavBar() {
        setActiveNavTab(R.id.nav_tab_instances);
    }
}
