package org.levimc.launcher.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.levimc.launcher.core.minecraft.MinecraftLauncher;
import org.levimc.launcher.core.versions.GameVersion;
import org.levimc.launcher.core.versions.VersionManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Provides local instance operations without modifying the Minecraft runtime. */
public final class InstanceFeatureManager {
    private static final String PREFS = "instance_feature_options";
    private static final String HISTORY_FILE = "instance_change_history.log";

    public interface Callback {
        void onSuccess(String message);
        void onError(String message);
    }

    private InstanceFeatureManager() { }

    public static void cloneInstance(Context context, GameVersion source, boolean includeData, Callback callback) {
        if (source == null) {
            callback.onError("No instance selected");
            return;
        }
        if (source.isInstalled) {
            callback.onError("Installed Minecraft cannot be cloned. Import its APK as a custom instance first.");
            return;
        }
        LauncherTaskQueue.submit(() -> {
            try {
                String targetName = nextCloneName(context, source.directoryName);
                File targetVersionDir = LauncherStorage.getVersionDir(context, targetName);
                if (targetVersionDir.exists()) deleteTree(targetVersionDir);
                copyTree(source.versionDir, targetVersionDir);

                if (includeData) {
                    String sourceProfile = MinecraftLauncher.getStorageProfileId(source);
                    String targetProfile = LauncherStorage.sanitizeProfileId(targetName);
                    copyTree(LauncherStorage.getProfileGameDataDir(context, sourceProfile, false),
                            LauncherStorage.getProfileGameDataDir(context, targetProfile, false));
                    copyTree(LauncherStorage.getProfileGameDataDir(context, sourceProfile, true),
                            LauncherStorage.getProfileGameDataDir(context, targetProfile, true));
                    copyTree(LauncherStorage.getProfileModsDir(context, sourceProfile),
                            LauncherStorage.getProfileModsDir(context, targetProfile));
                }

                VersionManager.get(context).loadAllVersions();
                addHistory(context, targetName, "Instance cloned", includeData ? "APK, worlds, packs, settings and mods copied" : "APK files copied only");
                callback.onSuccess("Created clone: " + targetName);
            } catch (Exception error) {
                callback.onError("Clone failed: " + safeMessage(error));
            }
        });
    }

    public static void setTestLabel(Context context, GameVersion version, boolean enabled) {
        if (version == null) return;
        String profile = MinecraftLauncher.getStorageProfileId(version);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("test_" + profile, enabled).apply();
        addHistory(context, version.directoryName, enabled ? "Test label enabled" : "Test label removed", "");
    }

    public static boolean isTestInstance(Context context, GameVersion version) {
        if (version == null) return false;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("test_" + MinecraftLauncher.getStorageProfileId(version), false);
    }

    public static void addHistory(Context context, String instance, String action, String detail) {
        synchronized (InstanceFeatureManager.class) {
            File history = new File(context.getFilesDir(), HISTORY_FILE);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            try (FileWriter writer = new FileWriter(history, true)) {
                writer.write(timestamp + "\t" + safeField(instance) + "\t" + safeField(action) + "\t" + safeField(detail) + "\n");
            } catch (IOException ignored) { }
        }
    }

    public static List<String> getRecentHistory(Context context, String instance, int limit) {
        List<String> all = new ArrayList<>();
        File history = new File(context.getFilesDir(), HISTORY_FILE);
        if (!history.exists()) return all;
        try (BufferedReader reader = new BufferedReader(new FileReader(history))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (instance == null || instance.isEmpty() || line.contains("\t" + instance + "\t")) all.add(line);
            }
        } catch (IOException ignored) { }
        List<String> result = new ArrayList<>();
        for (int index = all.size() - 1; index >= 0 && result.size() < limit; index--) result.add(all.get(index));
        return result;
    }

    public static long getInstanceEstimatedSize(Context context, GameVersion version) {
        if (version == null) return 0L;
        long size = directorySize(version.versionDir);
        String profile = MinecraftLauncher.getStorageProfileId(version);
        size += directorySize(LauncherStorage.getProfileGameDataDir(context, profile, false));
        size += directorySize(LauncherStorage.getProfileGameDataDir(context, profile, true));
        size += directorySize(LauncherStorage.getProfileModsDir(context, profile));
        return size;
    }

    public static long directorySize(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) total += directorySize(child);
        return total;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1048576L) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824L) return String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0);
        return String.format(Locale.getDefault(), "%.2f GB", bytes / 1073741824.0);
    }

    private static String nextCloneName(Context context, String base) {
        String prefix = LauncherStorage.sanitizeProfileId((base == null ? "Minecraft" : base) + "_copy");
        String candidate = prefix;
        int index = 2;
        while (LauncherStorage.getVersionDir(context, candidate).exists()) candidate = prefix + "_" + index++;
        return candidate;
    }

    private static void copyTree(File source, File target) throws IOException {
        if (source == null || !source.exists()) return;
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) throw new IOException("Cannot create " + target.getName());
            File[] children = source.listFiles();
            if (children != null) for (File child : children) copyTree(child, new File(target, child.getName()));
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create target directory");
        try (FileInputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[131072];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private static String safeField(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ');
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
