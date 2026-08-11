package org.levimc.launcher.util;

import android.app.Activity;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.levimc.launcher.core.versions.GameVersion;
import org.levimc.launcher.core.versions.VersionManager;
import org.levimc.launcher.ui.dialogs.InstallProgressDialog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Downloads Minecraft APKs safely: partial files survive a pause only, not an error or cancellation. */
public class ApkDownloadManager {
    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final InstallProgressDialog progressDialog;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object pauseLock = new Object();
    private volatile boolean cancelled;
    private volatile boolean paused;
    private volatile Future<?> activeDownload;
    private volatile String activeVersion = "";
    private volatile String expectedVersion = "";

    public ApkDownloadManager(Activity activity) {
        this.activity = activity;
        this.progressDialog = new InstallProgressDialog(activity);
        this.progressDialog.setCancelAction(this::cancelDownload);
        this.progressDialog.setPauseAction(this::togglePause);
    }

    public void downloadAndInstall(String urlString, String fileName) {
        downloadAndInstall(urlString, fileName, extractVersion(fileName));
    }

    /** The catalog passes its discovered version so an incorrectly named asset cannot be installed silently. */
    public void downloadAndInstall(String urlString, String fileName, String expectedMinecraftVersion) {
        if (activeDownload != null && !activeDownload.isDone()) {
            Toast.makeText(activity, "Загрузка уже выполняется", Toast.LENGTH_SHORT).show();
            return;
        }
        cancelled = false;
        paused = false;
        activeVersion = extractVersion(fileName);
        expectedVersion = normalizeVersion(expectedMinecraftVersion);
        DownloadHistoryStore.add(activity, activeVersion, "download", "started", urlString);
        progressDialog.setPaused(false);
        progressDialog.setTitleText("Загрузка Minecraft...");
        progressDialog.setStatusText("Подключение...");
        progressDialog.setProgress(0);
        if (!progressDialog.isShowing()) progressDialog.show();
        activeDownload = executor.submit(() -> download(urlString, fileName));
    }

    /** Cancelling deletes the unfinished .part file so no invalid APK is left behind. */
    public void cancelDownload() {
        cancelled = true;
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
        Future<?> future = activeDownload;
        if (future != null) future.cancel(true);
    }

    /** Pausing intentionally keeps the .part file and resumes with HTTP Range when possible. */
    public void togglePause() {
        if (cancelled || activeDownload == null || activeDownload.isDone()) return;
        synchronized (pauseLock) {
            paused = !paused;
            if (!paused) pauseLock.notifyAll();
        }
        progressDialog.setPaused(paused);
        postUi(() -> {
            if (progressDialog.isShowing()) {
                progressDialog.setStatusText(paused ? "Загрузка приостановлена" : "Продолжение загрузки...");
            }
        });
        DownloadHistoryStore.add(activity, activeVersion, "download", paused ? "paused" : "resumed", "");
    }

    private void awaitIfPaused() throws DownloadCancelledException {
        synchronized (pauseLock) {
            while (paused && !cancelled) {
                try {
                    pauseLock.wait(500L);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new DownloadCancelledException();
                }
            }
        }
        if (cancelled || Thread.currentThread().isInterrupted()) throw new DownloadCancelledException();
    }

    private void download(String urlString, String fileName) {
        File downloadDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "levi apk");
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            DownloadHistoryStore.add(activity, activeVersion, "download", "failed", "Не удалось создать папку загрузок");
            postError("Не удалось создать папку загрузок");
            return;
        }
        String safeName = fileName == null || fileName.trim().isEmpty() ? "minecraft.apk" : new File(fileName).getName();
        File outputFile = new File(downloadDir, safeName);
        File partFile = new File(downloadDir, safeName + ".part");
        HttpURLConnection connection = null;
        try {
            long offset = partFile.exists() ? partFile.length() : 0L;
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            if (offset > 0L) connection.setRequestProperty("Range", "bytes=" + offset + "-");
            connection.connect();

            int responseCode = connection.getResponseCode();
            boolean append = offset > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL;
            if (responseCode != HttpURLConnection.HTTP_OK && !append) {
                throw new IOException("Ошибка сервера: " + responseCode);
            }
            if (!append) offset = 0L;

            long responseLength = connection.getContentLengthLong();
            long totalLength = responseLength > 0L ? offset + responseLength : -1L;
            try (InputStream input = connection.getInputStream();
                 RandomAccessFile output = new RandomAccessFile(partFile, "rw")) {
                if (append) output.seek(offset); else output.setLength(0L);
                byte[] buffer = new byte[131072];
                long downloaded = offset;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    awaitIfPaused();
                    output.write(buffer, 0, count);
                    downloaded += count;
                    if (totalLength > 0L) {
                        postProgress((int) Math.min(100L, downloaded * 100L / totalLength),
                                "Загрузка: " + formatMb(downloaded) + " / " + formatMb(totalLength));
                    } else {
                        postProgress(0, "Загрузка: " + formatMb(downloaded));
                    }
                }
            }

            awaitIfPaused();
            if (totalLength > 0L && partFile.length() != totalLength) {
                throw new IOException("Файл скачан не полностью");
            }
            if (outputFile.exists() && !outputFile.delete()) throw new IOException("Не удалось заменить старый APK");
            if (!partFile.renameTo(outputFile)) throw new IOException("Не удалось сохранить APK");
            DownloadHistoryStore.add(activity, activeVersion, "download", "completed", outputFile.getName());
            startAutoInstall(outputFile);
        } catch (DownloadCancelledException cancelledError) {
            deleteQuietly(partFile);
            DownloadHistoryStore.add(activity, activeVersion, "download", "cancelled", "");
            postCancelled();
        } catch (Exception error) {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                deleteQuietly(partFile);
                DownloadHistoryStore.add(activity, activeVersion, "download", "cancelled", "");
                postCancelled();
            } else {
                deleteQuietly(partFile);
                // A final .apk exists only after the complete download was renamed.
                if (outputFile.exists() && outputFile.length() == 0L) deleteQuietly(outputFile);
                String message = "Ошибка загрузки: " + safeMessage(error);
                DownloadHistoryStore.add(activity, activeVersion, "download", "failed", message);
                postError(message);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void startAutoInstall(File apkFile) {
        postUi(() -> {
            if (!progressDialog.isShowing()) return;
            progressDialog.setTitleText("Установка...");
            progressDialog.setStatusText("Извлечение файлов игры...");
            progressDialog.setProgress(0);
        });

        Uri apkUri = Uri.fromFile(apkFile);
        String versionName = ApkUtils.extractMinecraftVersionNameFromUri(activity, apkUri);
        if ("Error Apk".equals(versionName)) {
            deleteQuietly(apkFile);
            DownloadHistoryStore.add(activity, activeVersion, "download", "failed", "Downloaded file is not a Minecraft APK");
            postError("Скачанный файл не является Minecraft APK");
            return;
        }
        if (!expectedVersion.isEmpty() && !expectedVersion.equals(normalizeVersion(versionName))) {
            String detail = "Expected " + expectedVersion + ", got " + versionName;
            deleteQuietly(apkFile);
            DownloadHistoryStore.add(activity, activeVersion, "download", "failed", detail);
            postError("Версия скачанного APK не совпадает с выбранной: " + detail);
            return;
        }
        final String installedVersion = versionName;
        GameVersion existing = findExistingVersion(installedVersion);
        boolean autoBackup = activity.getSharedPreferences("launcher_options", Activity.MODE_PRIVATE)
                .getBoolean("auto_backup_before_update", true);
        if (existing != null && autoBackup) {
            backupBeforeUpdate(existing, apkFile, installedVersion);
            return;
        }
        installDownloadedApk(apkFile, installedVersion);
    }

    private void backupBeforeUpdate(GameVersion existing, File apkFile, String installedVersion) {
        postUi(() -> {
            if (progressDialog.isShowing()) {
                progressDialog.setTitleText("Резервная копия...");
                progressDialog.setStatusText("Сохранение данных перед обновлением Minecraft...");
                progressDialog.setProgress(0);
            }
        });
        new InstanceBackupManager(activity).backup(existing, new InstanceBackupManager.BackupCallback() {
            @Override public void onStarted() { }
            @Override public void onProgress(int progress) { postProgress(progress, "Резервная копия: " + progress + "%"); }
            @Override public void onSuccess(String path) {
                DownloadHistoryStore.add(activity, installedVersion, "backup", "completed", path);
                installDownloadedApk(apkFile, installedVersion);
            }
            @Override public void onError(String message) {
                DownloadHistoryStore.add(activity, installedVersion, "backup", "failed", message);
                postError("Автоматическая резервная копия не создана. Установка отменена: " + message);
            }
        });
    }

    private GameVersion findExistingVersion(String versionName) {
        try {
            VersionManager manager = VersionManager.get(activity);
            for (GameVersion item : manager.getCustomVersions()) {
                if (versionName.equals(item.versionCode)) return item;
            }
            for (GameVersion item : manager.getInstalledVersions()) {
                if (versionName.equals(item.versionCode)) return item;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void installDownloadedApk(File apkFile, String installedVersion) {
        Uri apkUri = Uri.fromFile(apkFile);
        final String dirName = "Minecraft_" + installedVersion;
        ApkInstaller installer = new ApkInstaller(activity, executor, new ApkInstaller.InstallCallback() {
            @Override public void onProgress(int progress) { postProgress(progress, "Установка: " + progress + "%"); }
            @Override public void onSuccess(String vName) {
                DownloadHistoryStore.add(activity, installedVersion, "install", "completed", vName);
                postUi(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    deleteQuietly(apkFile);
                    Toast.makeText(activity, "Установка завершена: " + vName, Toast.LENGTH_SHORT).show();
                    VersionManager.get(activity).loadAllVersions();
                    if (activity instanceof org.levimc.launcher.ui.activities.InstancesActivity) {
                        ((org.levimc.launcher.ui.activities.InstancesActivity) activity).refreshVersions();
                    }
                });
            }
            @Override public void onError(String errorMsg) {
                deleteQuietly(apkFile);
                DownloadHistoryStore.add(activity, installedVersion, "install", "failed", errorMsg);
                postError("Ошибка установки: " + errorMsg);
            }
        });
        installer.install(apkUri, dirName);
    }

    private void postProgress(int progress, String status) {
        postUi(() -> {
            if (progressDialog.isShowing()) {
                progressDialog.setProgress(progress);
                progressDialog.setStatusText(status);
            }
        });
    }

    private void postError(String error) {
        postUi(() -> {
            if (progressDialog.isShowing()) progressDialog.dismiss();
            Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
        });
    }

    private void postCancelled() {
        postUi(() -> {
            if (progressDialog.isShowing()) progressDialog.dismiss();
            Toast.makeText(activity, "Загрузка отменена", Toast.LENGTH_SHORT).show();
        });
    }

    private void postUi(Runnable action) {
        mainHandler.post(() -> {
            if (!activity.isFinishing() && (android.os.Build.VERSION.SDK_INT < 17 || !activity.isDestroyed())) action.run();
        });
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            try { file.delete(); } catch (Exception ignored) { }
        }
    }

    private static String normalizeVersion(String version) {
        if (version == null) return "";
        String[] values = version.replaceAll("[^0-9.]", "").split("\\.");
        StringBuilder normalized = new StringBuilder();
        for (String value : values) {
            if (value.isEmpty()) continue;
            try {
                if (normalized.length() > 0) normalized.append('.');
                normalized.append(Integer.parseInt(value));
            } catch (NumberFormatException ignored) { return ""; }
        }
        return normalized.toString();
    }

    private static String extractVersion(String fileName) {
        if (fileName == null) return "";
        return fileName.replace("minecraft_", "").replace(".apk", "");
    }

    private static String formatMb(long bytes) {
        return String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0);
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static final class DownloadCancelledException extends IOException { }
}
