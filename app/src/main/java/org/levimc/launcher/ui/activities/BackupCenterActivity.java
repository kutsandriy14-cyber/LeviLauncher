package org.levimc.launcher.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import org.levimc.launcher.R;
import org.levimc.launcher.core.versions.GameVersion;
import org.levimc.launcher.core.versions.VersionManager;
import org.levimc.launcher.ui.animation.DynamicAnim;
import org.levimc.launcher.util.InstanceBackupManager;

/** One-screen access to the existing full instance backup format. */
public class BackupCenterActivity extends BaseActivity {
    private TextView instanceName;
    private TextView status;
    private GameVersion currentVersion;
    private InstanceBackupManager backupManager;
    private ActivityResultLauncher<String> restorePicker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup_center);
        instanceName = findViewById(R.id.backup_current_instance);
        status = findViewById(R.id.backup_status);
        backupManager = new InstanceBackupManager(this);
        restorePicker = registerForActivityResult(new ActivityResultContracts.GetContent(), this::restoreSelectedFile);

        Button backup = findViewById(R.id.backup_create);
        Button restore = findViewById(R.id.backup_restore);
        backup.setOnClickListener(v -> createBackup());
        restore.setOnClickListener(v -> restorePicker.launch("application/*"));
        DynamicAnim.applyPressScale(backup);
        DynamicAnim.applyPressScale(restore);
        refreshSelectedVersion();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSelectedVersion();
    }

    private void refreshSelectedVersion() {
        try {
            currentVersion = VersionManager.get(this).getSelectedVersion();
        } catch (Exception ignored) {
            currentVersion = null;
        }
        if (currentVersion == null) {
            instanceName.setText("No instance selected");
            status.setText("Open Instances, select Minecraft, then return here.");
        } else {
            instanceName.setText(currentVersion.displayName == null || currentVersion.displayName.isEmpty()
                    ? currentVersion.versionCode : currentVersion.displayName);
            status.setText("Ready. Backups are written to Downloads/LeviLauncher/Backups.");
        }
    }

    private void createBackup() {
        if (currentVersion == null) {
            Toast.makeText(this, "Select a Minecraft instance first", Toast.LENGTH_LONG).show();
            return;
        }
        backupManager.backup(currentVersion, new InstanceBackupManager.BackupCallback() {
            @Override public void onStarted() { status.setText("Preparing backup…"); }
            @Override public void onProgress(int progress) { status.setText("Creating backup: " + progress + "%"); }
            @Override public void onSuccess(String displayPath) {
                status.setText("Backup saved: " + displayPath);
                Toast.makeText(BackupCenterActivity.this, "Backup created", Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String message) {
                status.setText("Backup failed: " + message);
                Toast.makeText(BackupCenterActivity.this, "Backup failed", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void restoreSelectedFile(Uri uri) {
        if (uri == null) return;
        backupManager.restore(uri, new InstanceBackupManager.RestoreCallback() {
            @Override public void onStarted() { status.setText("Validating backup…"); }
            @Override public void onProgress(int progress) { status.setText("Restoring backup: " + progress + "%"); }
            @Override public void onSuccess(String restoredName) {
                status.setText("Restored: " + restoredName);
                Toast.makeText(BackupCenterActivity.this, "Backup restored", Toast.LENGTH_SHORT).show();
                refreshSelectedVersion();
            }
            @Override public void onError(String message) {
                status.setText("Restore failed: " + message);
                Toast.makeText(BackupCenterActivity.this, "Restore failed", Toast.LENGTH_LONG).show();
            }
        });
    }
}
