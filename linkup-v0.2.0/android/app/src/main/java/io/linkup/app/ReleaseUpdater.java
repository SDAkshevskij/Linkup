package io.linkup.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.LinearLayout;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class ReleaseUpdater {
    private static final String REPO = "SDAkshevskij/Linkup";
    private static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final long APK_LIMIT = 100 * 1024 * 1024;
    private static final int ALLOW_INSTALL = 40;
    private static final int INSTALL = 41;
    private final Activity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private File pending;
    private boolean active;
    private boolean closed;
    private AtomicBoolean cancelled;

    ReleaseUpdater(Activity activity) {
        this.activity = activity;
    }

    void check() {
        if (active || closed) return;
        run("Проверяем обновления…", (stop, progress) -> {
            PackageInfo current = activity.getPackageManager().getPackageInfo(
                    activity.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            Asset release = readLatest(stop, current.getLongVersionCode());
            return () -> {
                if (release == null) {
                    message("Обновления не найдены");
                    return;
                }
                new AlertDialog.Builder(activity).setTitle("LinkUp " + release.tag)
                        .setMessage(release.notes.isEmpty() ? "Доступно обновление" : release.notes)
                        .setNegativeButton("Позже", null)
                        .setPositiveButton("Скачать", (dialog, which) -> download(release, current)).show();
            };
        });
    }

    private Asset readLatest(AtomicBoolean stop, long installed) throws Exception {
        HttpURLConnection connection = open(LATEST);
        try {
            int status = connection.getResponseCode();
            if (status == 404) return null;
            requireOk(status);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                copy(input, body, stop, 2 * 1024 * 1024, -1, null, ignored -> {});
            }
            JSONObject release = new JSONObject(new String(body.toByteArray(), StandardCharsets.UTF_8));
            if (release.getBoolean("draft") || release.getBoolean("prerelease")) return null;
            String tag = release.getString("tag_name");
            if (!tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IOException("Неизвестный формат релиза");
            JSONArray assets = release.getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.getString("name");
                if (!name.matches("LinkUp-Android-[1-9][0-9]{0,8}\\.apk")) continue;
                long version = Long.parseLong(name.substring("LinkUp-Android-".length(), name.length() - 4));
                if (version <= installed) return null;
                String url = "https://github.com/" + REPO + "/releases/download/" + tag + "/" + name;
                String digest = asset.getString("digest");
                long size = asset.getLong("size");
                if (!url.equals(asset.getString("browser_download_url")) || !digest.matches("sha256:[0-9a-f]{64}")
                        || size <= 0 || size > APK_LIMIT) throw new IOException("Некорректный файл релиза");
                String notes = release.optString("body", "");
                return new Asset(tag, notes.substring(0, Math.min(4000, notes.length())), url, digest.substring(7), size, version);
            }
            throw new IOException("В релизе нет APK LinkUp");
        } finally {
            connection.disconnect();
        }
    }

    private void download(Asset asset, PackageInfo current) {
        run("Скачиваем обновление…", (stop, progress) -> {
            File directory = new File(activity.getCacheDir(), "updates");
            Files.createDirectories(directory.toPath());
            File partial = File.createTempFile("linkup-", ".apk", activity.getCacheDir());
            File verified = new File(directory, "linkup.apk");
            try {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                HttpURLConnection connection = open(asset.url);
                try {
                    requireOk(connection.getResponseCode());
                    try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(partial.toPath())) {
                        copy(input, output, stop, asset.size, asset.size, sha, progress);
                    }
                } finally {
                    connection.disconnect();
                }
                if (!asset.hash.equals(toHex(sha.digest()))) throw new IOException("Контрольная сумма APK не совпадает");
                PackageInfo candidate = activity.getPackageManager().getPackageArchiveInfo(
                        partial.getPath(), PackageManager.GET_SIGNING_CERTIFICATES);
                verifyPackage(current, candidate, asset.version);
                if (stop.get()) throw new IOException("Загрузка отменена");
                Files.move(partial.toPath(), verified.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return () -> offerInstall(verified);
            } finally {
                Files.deleteIfExists(partial.toPath());
            }
        });
    }

    static void verifyPackage(PackageInfo current, PackageInfo candidate, long expected) throws IOException {
        if (candidate == null || !current.packageName.equals(candidate.packageName)
                || candidate.getLongVersionCode() != expected || expected <= current.getLongVersionCode()) {
            throw new IOException("APK не является обновлением LinkUp");
        }
        if (current.signingInfo == null || candidate.signingInfo == null) {
            throw new IOException("Подпись APK недоступна");
        }
        Set<Signature> installed = new HashSet<>(Arrays.asList(current.signingInfo.getApkContentsSigners()));
        Set<Signature> downloaded = new HashSet<>(Arrays.asList(candidate.signingInfo.getApkContentsSigners()));
        if (installed.isEmpty() || !installed.equals(downloaded)) {
            throw new IOException("Подпись обновления отличается. Сначала сохраните локальные данные; см. инструкцию перехода на релиз.");
        }
    }

    private void offerInstall(File file) {
        pending = file;
        new AlertDialog.Builder(activity).setTitle("Обновление готово")
                .setMessage("Android запросит подтверждение установки. Локальные данные сохранятся.")
                .setNegativeButton("Позже", (dialog, which) -> discard())
                .setPositiveButton("Установить", (dialog, which) -> install()).show();
    }

    private void install() {
        if (pending == null) return;
        try {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())), ALLOW_INSTALL);
                return;
            }
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".updates", pending);
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT, true);
            activity.startActivityForResult(intent, INSTALL);
        } catch (RuntimeException unavailable) {
            discard();
            message("Системная установка недоступна");
        }
    }

    boolean onActivityResult(int request, int result) {
        if (request == ALLOW_INSTALL) {
            if (activity.getPackageManager().canRequestPackageInstalls() && pending != null) {
                offerInstall(pending);
            } else {
                discard();
                message("Android не разрешил установку из LinkUp");
            }
            return true;
        }
        if (request != INSTALL) return false;
        discard();
        if (result != Activity.RESULT_OK) message("Установка отменена или Android отклонил APK");
        return true;
    }

    private void discard() {
        if (pending != null) pending.delete();
        pending = null;
    }

    private void message(String text) {
        new AlertDialog.Builder(activity).setTitle("Обновления LinkUp").setMessage(text)
                .setPositiveButton("ОК", null).show();
    }

    private void run(String title, Task task) {
        active = true;
        AtomicBoolean stop = new AtomicBoolean();
        cancelled = stop;
        TextView label = new TextView(activity);
        label.setText(title);
        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding / 2);
        layout.addView(label);
        layout.addView(bar);
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(layout)
                .setNegativeButton("Отмена", (d, w) -> stop.set(true))
                .setOnCancelListener(d -> stop.set(true)).show();
        worker.execute(() -> {
            Runnable done;
            try {
                done = task.run(stop, percent -> activity.runOnUiThread(() -> {
                    bar.setIndeterminate(false);
                    bar.setProgress(percent);
                    label.setText(title + " " + percent + "%");
                }));
            } catch (Exception error) {
                String reason = error instanceof java.net.SocketTimeoutException
                        ? "Сервер не ответил вовремя" : error instanceof IOException
                        ? error.getMessage() : "Не удалось проверить обновление";
                done = () -> message(reason == null ? "Не удалось проверить обновление" : reason);
            }
            Runnable result = done;
            activity.runOnUiThread(() -> {
                active = false;
                if (closed) return;
                dialog.dismiss();
                if (!stop.get()) result.run();
            });
        });
    }

    void close() {
        closed = true;
        if (cancelled != null) cancelled.set(true);
        worker.shutdownNow();
    }

    private static HttpURLConnection open(String address) throws IOException {
        for (int redirect = 0; redirect < 5; redirect++) {
            URL url = new URL(address);
            if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null || url.getPort() != -1
                    || !Arrays.asList("api.github.com", "github.com", "release-assets.githubusercontent.com",
                            "objects.githubusercontent.com").contains(url.getHost())) {
                throw new IOException("Недопустимый адрес загрузки");
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("User-Agent", "LinkUp-Updater");
            try {
                int status = connection.getResponseCode();
                if (status != 301 && status != 302 && status != 303 && status != 307 && status != 308) return connection;
                String location = connection.getHeaderField("Location");
                if (location == null) throw new IOException("GitHub не вернул адрес загрузки");
                address = new URL(url, location).toString();
            } catch (IOException error) {
                connection.disconnect();
                throw error;
            }
            connection.disconnect();
        }
        throw new IOException("Слишком много перенаправлений");
    }

    private static void requireOk(int status) throws IOException {
        if (status != 200) throw new IOException("GitHub вернул HTTP " + status);
    }

    private static void copy(InputStream input, OutputStream output, AtomicBoolean stop,
                             long limit, long expected, MessageDigest digest, Progress progress) throws IOException {
        long deadline = SystemClock.elapsedRealtime() + (expected > 0 ? 180000 : 20000);
        long total = 0;
        int reported = -1;
        byte[] buffer = new byte[32768];
        int length;
        while (!stop.get() && (length = input.read(buffer)) != -1) {
            total += length;
            if (total > limit || SystemClock.elapsedRealtime() > deadline) throw new IOException("Превышен лимит загрузки");
            output.write(buffer, 0, length);
            if (digest != null) digest.update(buffer, 0, length);
            if (expected > 0) {
                int percent = (int) (100 * total / expected);
                if (percent != reported) progress.set(percent);
                reported = percent;
            }
        }
        if (stop.get() || (expected >= 0 && total != expected)) throw new IOException("Загрузка не завершена");
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(Character.forDigit((b & 0xff) >>> 4, 16)).append(Character.forDigit(b & 15, 16));
        return hex.toString();
    }

    private interface Task {
        Runnable run(AtomicBoolean stop, Progress progress) throws Exception;
    }

    private interface Progress {
        void set(int percent);
    }

    private static final class Asset {
        final String tag, notes, url, hash;
        final long size, version;
        Asset(String tag, String notes, String url, String hash, long size, long version) {
            this.tag = tag;
            this.notes = notes;
            this.url = url;
            this.hash = hash;
            this.size = size;
            this.version = version;
        }
    }
}
