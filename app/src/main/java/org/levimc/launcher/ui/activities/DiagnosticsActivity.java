package org.levimc.launcher.ui.activities;

import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.levimc.launcher.BuildConfig;
import org.levimc.launcher.R;
import org.levimc.launcher.ui.animation.DynamicAnim;
import org.levimc.launcher.util.LauncherStorage;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/** A shareable, local-only support report. No diagnostic data is uploaded by this screen. */
public class DiagnosticsActivity extends BaseActivity {
    private TextView reportView;
    private String report = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);
        reportView = findViewById(R.id.diagnostics_report_text);
        Button refresh = findViewById(R.id.diagnostics_refresh);
        Button copy = findViewById(R.id.diagnostics_copy);
        Button save = findViewById(R.id.diagnostics_save);
        refresh.setOnClickListener(v -> refreshReport());
        copy.setOnClickListener(v -> copyReport());
        save.setOnClickListener(v -> saveReport(true));
        DynamicAnim.applyPressScale(refresh);
        DynamicAnim.applyPressScale(copy);
        DynamicAnim.applyPressScale(save);
        refreshReport();
    }

    private void refreshReport() {
        report = buildReport();
        reportView.setText(report);
        saveReport(false);
    }

    private String buildReport() {
        StringBuilder out = new StringBuilder();
        out.append("LeviLauncher diagnostic report\n");
        out.append("Created: ").append(DateFormat.getDateTimeInstance().format(new Date())).append("\n\n");
        out.append("Launcher\n");
        out.append("  Version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        out.append("  Package: ").append(getPackageName()).append("\n");
        out.append("  Update source: ").append(BuildConfig.UPDATE_GITHUB_OWNER).append("/").append(BuildConfig.UPDATE_GITHUB_REPO).append("\n");
        out.append("  Signing certificate SHA-256: ").append(getSigningCertificateSha256()).append("\n\n");
        out.append("Device\n");
        out.append("  Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        out.append("  Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        out.append("  ABI: ").append(String.join(", ", Build.SUPPORTED_ABIS)).append("\n");
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(memory);
        out.append("  Free memory: ").append(formatBytes(memory.availMem)).append("\n");
        out.append("  Low memory: ").append(memory.lowMemory ? "yes" : "no").append("\n\n");
        File appRoot = LauncherStorage.getAppRoot(this);
        out.append("Storage\n");
        out.append("  App data: ").append(appRoot.getAbsolutePath()).append("\n");
        out.append("  Free space: ").append(formatBytes(appRoot.getUsableSpace())).append("\n\n");
        out.append("Recent crash files\n");
        File[] crashes = LauncherStorage.getCrashLogsDir(this).listFiles(File::isFile);
        if (crashes == null || crashes.length == 0) {
            out.append("  None found\n");
        } else {
            Arrays.sort(crashes, Comparator.comparingLong(File::lastModified).reversed());
            for (int i = 0; i < Math.min(5, crashes.length); i++) {
                File crash = crashes[i];
                out.append("  ").append(crash.getName()).append(" — ")
                        .append(DateFormat.getDateTimeInstance().format(new Date(crash.lastModified())))
                        .append(" (" ).append(formatBytes(crash.length())).append(")\n");
            }
        }
        return out.toString();
    }

    private void copyReport() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("LeviLauncher diagnostic report", report));
            Toast.makeText(this, "Diagnostic report copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveReport(boolean notify) {
        try {
            File dir = new File(LauncherStorage.getAppRoot(this), "diagnostics");
            LauncherStorage.ensureDir(dir);
            File reportFile = new File(dir, "latest-diagnostic.txt");
            try (FileOutputStream output = new FileOutputStream(reportFile, false)) {
                output.write(report.getBytes(StandardCharsets.UTF_8));
            }
            if (notify) Toast.makeText(this, "Saved: " + reportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            if (notify) Toast.makeText(this, "Could not save report: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getSigningCertificateSha256() {
        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info = getPackageManager().getPackageInfo(getPackageName(),
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
            } else {
                //noinspection deprecation
                info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            }
            Signature[] signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
            if (signatures == null || signatures.length == 0) return "unavailable";
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format(Locale.US, "%02x", value));
            return hex.toString();
        } catch (Exception ignored) {
            return "unavailable";
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024d && unit < units.length - 1) {
            value /= 1024d;
            unit++;
        }
        return String.format(Locale.US, "%.1f %s", value, units[Math.max(0, unit)]);
    }
}
