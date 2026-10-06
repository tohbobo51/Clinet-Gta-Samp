package com.artplaygames.sampclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pemeriksa & pengunduh update data/aset game.
 *
 * Alur:
 *   1. {@link #check(Listener)} mengambil manifest JSON dari server (URL ada di strings.xml:
 *      update_manifest_url), membandingkan angka "version" dengan versi yang terpasang.
 *   2. Jika lebih baru, {@link #download(Manifest, Listener)} mengunduh tiap file ke
 *      penyimpanan khusus aplikasi (Android/data/&lt;paket&gt;/files/game_data),
 *      memverifikasi ukuran &amp; SHA-256 bila ada di manifest, lalu menyimpan versi.
 *
 * Contoh manifest (update.json):
 * <pre>
 * {
 *   "version": 2,
 *   "title": "Update Roleplay Oktober",
 *   "description": "Menambahkan objek interior &amp; peta baru",
 *   "files": [
 *     {"path": "stream/objects.dff", "url": "https://cdn/…/objects.dff",
 *      "size": 1048576, "sha256": "…"}
 *   ]
 * }
 * </pre>
 *
 * Semua callback dipanggil di main thread.
 */
public final class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String PREFS = "game_update";
    private static final String KEY_VERSION = "installed_version";
    private static final String KEY_TITLE = "installed_title";

    /** Callback UI. Semua metode punya implementasi kosong — override yang dibutuhkan saja. */
    public static abstract class Listener {
        public void onNoUpdate() { }
        public void onUpdateAvailable(Manifest manifest) { }
        public void onCheckFailed(String message) { }
        public void onProgress(int percent, String detail) { }
        public void onInstalled(Manifest manifest) { }
        public void onFailed(String message) { }
    }

    /** Satu baris file dalam manifest. */
    public static final class FileEntry {
        public final String path;
        public final String url;
        public final long size;
        public final String sha256;

        FileEntry(String path, String url, long size, String sha256) {
            this.path = path;
            this.url = url;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    /** Isi update.json. */
    public static final class Manifest {
        public final int version;
        public final String title;
        public final String description;
        public final List<FileEntry> files;

        Manifest(int version, String title, String description, List<FileEntry> files) {
            this.version = version;
            this.title = title;
            this.description = description;
            this.files = files;
        }

        static Manifest parse(String json) throws JSONException {
            JSONObject root = new JSONObject(json);
            int version = root.optInt("version", 0);
            if (version <= 0) {
                throw new JSONException("Field \"version\" tidak valid");
            }
            String title = root.optString("title", "Update data game");
            String description = root.optString("description", "");

            List<FileEntry> files = new ArrayList<>();
            JSONArray array = root.optJSONArray("files");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    String path = item.getString("path");
                    String url = item.getString("url");
                    long size = item.optLong("size", 0L);
                    String sha = item.optString("sha256", "");
                    files.add(new FileEntry(path, url, size, sha));
                }
            }
            return new Manifest(version, title, description, files);
        }
    }

    private interface FileProgress {
        void onFileProgress(int percent);
    }

    private final Context appContext;
    private final String manifestUrl;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public UpdateManager(Context context, String manifestUrl) {
        this.appContext = context.getApplicationContext();
        this.manifestUrl = manifestUrl;
        this.prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------
    //  Info versi & lokasi penyimpanan
    // ------------------------------------------------------------------

    /**
     * Folder penyimpanan data game milik aplikasi.
     * Utama: Android/data/&lt;paket&gt;/files/game_data (external app-specific, tanpa izin).
     * Cadangan: files/game_data (internal) jika external tidak tersedia.
     */
    public File getGameDataDir() {
        File dir = appContext.getExternalFilesDir("game_data");
        if (dir == null) {
            dir = new File(appContext.getFilesDir(), "game_data");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Gagal membuat folder data game: " + dir);
        }
        return dir;
    }

    public int getInstalledVersion() {
        return prefs.getInt(KEY_VERSION, 0);
    }

    /** Judul data yang terpasang; string kosong bila belum pernah mengunduh. */
    public String getInstalledTitle() {
        return prefs.getString(KEY_TITLE, "");
    }

    // ------------------------------------------------------------------
    //  Aksi
    // ------------------------------------------------------------------

    /** Cek manifest update secara async. */
    public void check(final Listener listener) {
        cancelled.set(false);
        if (executor.isShutdown()) {
            post(() -> listener.onCheckFailed("Aplikasi sedang ditutup"));
            return;
        }
        executor.execute(() -> {
            try {
                final Manifest manifest = Manifest.parse(httpGet(manifestUrl));
                if (manifest.version <= getInstalledVersion()) {
                    post(listener::onNoUpdate);
                } else {
                    post(() -> listener.onUpdateAvailable(manifest));
                }
            } catch (final Exception e) {
                Log.w(TAG, "Cek update gagal", e);
                post(() -> listener.onCheckFailed(humanMessage(e)));
            }
        });
    }

    /** Unduh seluruh file dalam manifest ke penyimpanan aplikasi. */
    public void download(final Manifest manifest, final Listener listener) {
        cancelled.set(false);
        if (executor.isShutdown()) {
            post(() -> listener.onFailed("Aplikasi sedang ditutup"));
            return;
        }
        executor.execute(() -> {
            try {
                final int fileCount = manifest.files.size();
                if (fileCount == 0) {
                    throw new IOException("Manifest tidak memuat file apa pun");
                }
                File root = getGameDataDir();

                for (int i = 0; i < fileCount; i++) {
                    if (cancelled.get()) {
                        throw new IOException("Dibatalkan");
                    }
                    final int index = i;
                    final FileEntry entry = manifest.files.get(i);
                    final File target = resolveSafe(root, entry.path);
                    final String label = shortName(entry.path) + " (" + (index + 1) + "/" + fileCount + ")";

                    post(() -> listener.onProgress((index * 100) / fileCount, label));

                    fetchFile(entry, target, percent -> {
                        int overall = (index * 100 + percent) / fileCount;
                        post(() -> listener.onProgress(overall, label + " " + percent + "%"));
                    });

                    if (entry.path.toLowerCase().endsWith(".zip")) {
                        post(() -> listener.onProgress((index * 100) / fileCount, "Mengekstrak " + shortName(entry.path) + "…"));
                        unzip(target, root);
                    }
                }

                setInstalled(manifest);
                post(() -> listener.onInstalled(manifest));
            } catch (final Exception e) {
                Log.e(TAG, "Update gagal", e);
                post(() -> listener.onFailed(humanMessage(e)));
            }
        });
    }

    /** Batalkan unduhan yang sedang berjalan. */
    public void cancel() {
        cancelled.set(true);
    }

    /** Hentikan pekerjaan background (dipanggil dari onDestroy). */
    public void shutdown() {
        cancelled.set(true);
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------
    //  Implementasi jaringan & penyimpanan
    // ------------------------------------------------------------------

    private String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = open(urlStr);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " dari " + urlStr);
            }
            return readBody(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    private HttpURLConnection open(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "SampClient-Update/1.0");
        return conn;
    }

    private void fetchFile(FileEntry entry, File dest, FileProgress progress) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Gagal membuat folder " + parent.getName());
        }

        File tmp = new File(dest.getPath() + ".part");
        HttpURLConnection conn = open(entry.url);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " saat mengunduh " + entry.path);
            }
            long total = conn.getContentLength();
            long read = 0;
            int last = -1;
            int n;

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buffer = new byte[64 * 1024];
                while ((n = in.read(buffer)) != -1) {
                    if (cancelled.get()) {
                        throw new IOException("Dibatalkan");
                    }
                    out.write(buffer, 0, n);
                    read += n;
                    if (total > 0 && progress != null) {
                        int percent = (int) Math.min(100L, read * 100L / total);
                        if (percent != last) {
                            last = percent;
                            progress.onFileProgress(percent);
                        }
                    }
                }
                out.flush();
            }
        } finally {
            conn.disconnect();
        }

        if (cancelled.get()) {
            tmp.delete();
            throw new IOException("Dibatalkan");
        }
        if (entry.size > 0 && tmp.length() != entry.size) {
            tmp.delete();
            throw new IOException("Ukuran file tidak sesuai: " + entry.path);
        }
        if (!entry.sha256.isEmpty()) {
            String actual = sha256Of(tmp);
            if (!actual.equalsIgnoreCase(entry.sha256)) {
                tmp.delete();
                throw new IOException("Checksum tidak sesuai: " + entry.path);
            }
        }
        if (dest.exists() && !dest.delete()) {
            throw new IOException("Gagal menimpa " + entry.path);
        }
        if (!tmp.renameTo(dest)) {
            throw new IOException("Gagal menyimpan " + entry.path);
        }
    }

    private void unzip(File zipFile, File targetDir) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.BufferedInputStream(new java.io.FileInputStream(zipFile)))) {
            java.util.zip.ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (cancelled.get()) {
                    throw new IOException("Ekstraksi dibatalkan");
                }
                File newFile = resolveSafe(targetDir, ze.getName());
                if (ze.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /** Tolak path yang keluar dari folder data game (perlindungan path traversal). */
    private static File resolveSafe(File root, String relativePath) throws IOException {
        File target = new File(root, relativePath);
        String rootPath = root.getCanonicalPath();
        String targetPath = target.getCanonicalPath();
        if (!targetPath.startsWith(rootPath + File.separator)) {
            throw new IOException("Path tidak sah di manifest: " + relativePath);
        }
        return target;
    }

    private static String sha256Of(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, n);
                }
            }
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest.digest()) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 tidak tersedia", e);
        }
    }

    private void setInstalled(Manifest manifest) {
        prefs.edit()
                .putInt(KEY_VERSION, manifest.version)
                .putString(KEY_TITLE, manifest.title)
                .apply();
    }

    private static String readBody(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static String shortName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String humanMessage(Exception e) {
        String message = e.getMessage();
        if (message != null && message.contains("Dibatalkan")) {
            return "Dibatalkan";
        }
        if (e instanceof UnknownHostException) {
            return "Server update tidak ditemukan";
        }
        if (e instanceof SocketTimeoutException) {
            return "Koneksi timeout";
        }
        if (e instanceof JSONException) {
            return "Format update.json tidak valid";
        }
        if (message != null && !message.isEmpty()) {
            return message;
        }
        return e.getClass().getSimpleName();
    }

    private void post(Runnable runnable) {
        mainHandler.post(runnable);
    }
}
