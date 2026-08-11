package org.levimc.launcher.util;

import android.app.Activity;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

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

public class ApkDownloadManager {
    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final InstallProgressDialog progressDialog;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private volatile Future<?> activeDownload;

    public ApkDownloadManager(Activity activity) {
        this.activity = activity;
        this.progressDialog = new InstallProgressDialog(activity);
        this.progressDialog.setCancelAction(this::cancelDownload);
    }

    public void downloadAndInstall(String urlString, String fileName) {
        if (activeDownload != null && !activeDownload.isDone()) {
            Toast.makeText(activity, "Загрузка уже выполняется", Toast.LENGTH_SHORT).show();
            return;
        }
        cancelled = false;
        progressDialog.setTitleText("Загрузка Minecraft...");
        progressDialog.setStatusText("Подключение...");
        progressDialog.setProgress(0);
        if (!progressDialog.isShowing()) progressDialog.show();
        activeDownload = executor.submit(() -> download(urlString, fileName));
    }

    public void cancelDownload() {
        cancelled = true;
        Future<?> future = activeDownload;
        if (future != null) future.cancel(true);
        postCancelled();
    }

    private void download(String urlString, String fileName) {
        File downloadDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "levi apk");
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
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
                    if (cancelled || Thread.currentThread().isInterrupted()) throw new DownloadCancelledException();
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

            if (cancelled) throw new DownloadCancelledException();
            if (totalLength > 0L && partFile.length() != totalLength) {
                throw new IOException("Файл скачан не полностью");
            }
            if (outputFile.exists() && !outputFile.delete()) throw new IOException("Не удалось заменить старый APK");
            if (!partFile.renameTo(outputFile)) throw new IOException("Не удалось сохранить APK");
            startAutoInstall(outputFile);
        } catch (DownloadCancelledException cancelledError) {
            if (partFile.exists()) partFile.delete();
            postCancelled();
        } catch (Exception error) {
            // Only the .part file can remain after an interrupted network transfer.
            if (outputFile.exists() && outputFile.length() == 0L) outputFile.delete();
            postError("Ошибка загрузки: " + safeMessage(error));
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
        if ("Error Apk".equals(versionName)) versionName = "unknown";
        final String dirName = "Minecraft_" + versionName;
        ApkInstaller installer = new ApkInstaller(activity, executor, new ApkInstaller.InstallCallback() {
            @Override public void onProgress(int progress) { postProgress(progress, "Установка: " + progress + "%"); }
            @Override public void onSuccess(String vName) {
                postUi(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    if (apkFile.exists()) apkFile.delete();
                    Toast.makeText(activity, "Установка завершена: " + vName, Toast.LENGTH_SHORT).show();
                    VersionManager.get(activity).loadAllVersions();
                    if (activity instanceof org.levimc.launcher.ui.activities.InstancesActivity) {
                        ((org.levimc.launcher.ui.activities.InstancesActivity) activity).refreshVersions();
                    }
                });
            }
            @Override public void onError(String errorMsg) {
                if (apkFile.exists()) apkFile.delete();
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

    private static String formatMb(long bytes) {
        return String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0);
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static final class DownloadCancelledException extends IOException {}
}
