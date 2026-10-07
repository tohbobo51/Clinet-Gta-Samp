package com.artplaygames.sampclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Modul Downloader & Auto-Updater SAMP/CRMP (Differential / Segmen Update).
 *
 * Mendukung:
 *  1. Pengecekan integritas file game lokal (apakah file inti game sudah lengkap di penyimpanan).
 *  2. Pengecekan version.json remote dengan segmen/paket terpisah (base, patch, crmp).
 *  3. Differential update: Hanya mengunduh paket/file yang belum ada atau versinya berubah.
 *  4. UI Progress real-time: Persentase, kecepatan (MB/s), ukuran (MB/MB), dan ekstraksi unzip.
 */
public final class UpdateManager {

    private static final String TAG = "SampUpdater";
    private static final String PREFS = "samp_update_prefs";
    private static final String KEY_PKG_VER_PREFIX = "pkg_ver_";
    private static final String KEY_GLOBAL_VER = "global_version";

    /** Callback listener untuk proses pengecekan dan pengunduhan */
    public static abstract class Listener {
        public void onNoUpdate() { }
        public void onUpdateAvailable(Manifest manifest, List<PackageEntry> neededPackages) { }
        public void onCheckFailed(String message) { }
        public void onProgress(int percent, String detail, String speedText) { }
        public void onInstalled(Manifest manifest) { }
        public void onFailed(String message) { }
    }

    /** Entitas satu paket data (misal: Base Cache GTA SA atau Patch CRMP) */
    public static final class PackageEntry {
        public final String id;
        public final String name;
        public final int version;
        public final long size;
        public final String url;
        public final String sha256;
        public final String extractTo;
        public final List<String> requiredFiles;

        public PackageEntry(String id, String name, int version, long size, String url,
                            String sha256, String extractTo, List<String> requiredFiles) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.size = size;
            this.url = url;
            this.sha256 = sha256;
            this.extractTo = extractTo != null ? extractTo : "";
            this.requiredFiles = requiredFiles != null ? requiredFiles : new ArrayList<>();
        }
    }

    /** Manifest version.json */
    public static final class Manifest {
        public final int version;
        public final String clientVersion;
        public final List<PackageEntry> packages;

        public Manifest(int version, String clientVersion, List<PackageEntry> packages) {
            this.version = version;
            this.clientVersion = clientVersion;
            this.packages = packages;
        }

        public static Manifest parse(String json) throws JSONException {
            JSONObject root = new JSONObject(json);
            int version = root.optInt("version", 1);
            String clientVersion = root.optString("client_version", "1.0");

            List<PackageEntry> packageList = new ArrayList<>();

            // Dukungan format "packages" (multi-segmen)
            JSONArray pkgsArray = root.optJSONArray("packages");
            if (pkgsArray != null) {
                for (int i = 0; i < pkgsArray.length(); i++) {
                    JSONObject item = pkgsArray.getJSONObject(i);
                    String id = item.optString("id", "pkg_" + i);
                    String name = item.optString("name", id);
                    int pkgVer = item.optInt("version", 1);
                    long size = item.optLong("size", 0L);
                    String url = item.getString("url");
                    String sha256 = item.optString("sha256", "");
                    String extractTo = item.optString("extract_to", "");

                    List<String> reqFiles = new ArrayList<>();
                    JSONArray reqArray = item.optJSONArray("required_files");
                    if (reqArray != null) {
                        for (int j = 0; j < reqArray.length(); j++) {
                            reqFiles.add(reqArray.getString(j));
                        }
                    }
                    packageList.add(new PackageEntry(id, name, pkgVer, size, url, sha256, extractTo, reqFiles));
                }
            } else {
                // Fallback kompatibilitas format "files" lama
                JSONArray filesArray = root.optJSONArray("files");
                if (filesArray != null) {
                    for (int i = 0; i < filesArray.length(); i++) {
                        JSONObject item = filesArray.getJSONObject(i);
                        String path = item.getString("path");
                        String url = item.getString("url");
                        long size = item.optLong("size", 0L);
                        String sha = item.optString("sha256", "");
                        List<String> req = new ArrayList<>();
                        req.add(path);
                        packageList.add(new PackageEntry("file_" + i, path, 1, size, url, sha, "", req));
                    }
                }
            }

            return new Manifest(version, clientVersion, packageList);
        }
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

    /**
     * Folder penyimpanan data game.
     * Menggunakan Android/data/<package>/files (app-specific external storage, tanpa butuh izin runtime Android 11+).
     */
    public File getGameDataDir() {
        File dir = appContext.getExternalFilesDir(null);
        if (dir == null) {
            dir = new File(appContext.getFilesDir(), "game_data");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Gagal membuat folder game: " + dir);
        }
        return dir;
    }

    // ==================================================================
    //  1. Pemeriksaan Integritas Data Game Lokal
    // ==================================================================

    /**
     * Memeriksa apakah data game utama sudah ada dan lengkap di HP.
     */
    public boolean isGameDataComplete() {
        File gameDir = getGameDataDir();
        if (!gameDir.exists() || !gameDir.isDirectory()) {
            return false;
        }

        // File-file inti yang wajib ada agar game GTA SAMP dapat berjalan
        String[] coreFiles = new String[] {
                "anim/anim.img",
                "data/gta.dat",
                "texdb/samp.img"
        };

        for (String relPath : coreFiles) {
            File f = new File(gameDir, relPath);
            if (!f.exists()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mengevaluasi paket apa saja yang belum terpasang atau versinya usang
     * berdasarkan manifest remote yang diberikan.
     */
    public List<PackageEntry> filterNeededPackages(Manifest manifest) {
        List<PackageEntry> needed = new ArrayList<>();
        File gameDir = getGameDataDir();

        for (PackageEntry pkg : manifest.packages) {
            int localVer = prefs.getInt(KEY_PKG_VER_PREFIX + pkg.id, 0);

            // Periksa apakah file required benar-benar ada di penyimpanan fisik
            boolean filesExist = true;
            if (pkg.requiredFiles != null && !pkg.requiredFiles.isEmpty()) {
                for (String relFile : pkg.requiredFiles) {
                    File targetFile = new File(gameDir, relFile);
                    if (!targetFile.exists()) {
                        filesExist = false;
                        break;
                    }
                }
            } else {
                filesExist = (localVer >= pkg.version);
            }

            // Jika versi lokal lebih rendah ATAU file belum lengkap di disk -> butuh unduhan
            if (localVer < pkg.version || !filesExist) {
                needed.add(pkg);
            }
        }
        return needed;
    }

    // ==================================================================
    //  2. Pemeriksaan Pembaruan (version.json)
    // ==================================================================

    public void check(final Listener listener) {
        if (executor.isShutdown()) {
            return;
        }
        executor.execute(() -> {
            try {
                String json = httpGet(manifestUrl);
                Manifest manifest = Manifest.parse(json);
                List<PackageEntry> needed = filterNeededPackages(manifest);

                if (needed.isEmpty()) {
                    post(listener::onNoUpdate);
                } else {
                    post(() -> listener.onUpdateAvailable(manifest, needed));
                }
            } catch (final Exception e) {
                Log.w(TAG, "Gagal cek update: " + e.getMessage());
                post(() -> listener.onCheckFailed(humanMessage(e)));
            }
        });
    }

    // ==================================================================
    //  3. Differential Downloader & Extractor
    // ==================================================================

    public void download(final Manifest manifest, final List<PackageEntry> queue, final Listener listener) {
        cancelled.set(false);
        if (executor.isShutdown()) {
            post(() -> listener.onFailed("Aplikasi sedang ditutup"));
            return;
        }

        executor.execute(() -> {
            try {
                if (queue.isEmpty()) {
                    post(() -> listener.onInstalled(manifest));
                    return;
                }

                File gameDir = getGameDataDir();
                final int totalItems = queue.size();

                // Hitung total byte semua file yang akan diunduh untuk kalkulasi kecepatan
                long totalBytesAll = 0;
                for (PackageEntry p : queue) {
                    totalBytesAll += p.size;
                }

                long overallBytesDownloaded = 0;

                for (int i = 0; i < totalItems; i++) {
                    if (cancelled.get()) {
                        throw new IOException("Proses unduhan dibatalkan");
                    }

                    final PackageEntry pkg = queue.get(i);
                    final String pkgPrefix = "(" + (i + 1) + "/" + totalItems + ") " + pkg.name;

                    // Nama file unduhan sementara di cache
                    String fileName = pkg.id + ".zip";
                    File downloadFile = new File(gameDir, fileName);

                    final long currentBaseDownloaded = overallBytesDownloaded;
                    final long totalBytesFinal = totalBytesAll;

                    // Unduh dengan pelaporan speed dan progress real-time
                    fetchFileWithSpeed(pkg.url, downloadFile, pkg.size, new SpeedCallback() {
                        @Override
                        public void onProgress(long fileDownloaded, long fileSize, float speedMBs) {
                            long totalDownloaded = currentBaseDownloaded + fileDownloaded;
                            int percent = totalBytesFinal > 0
                                    ? (int) ((totalDownloaded * 100) / totalBytesFinal)
                                    : (int) ((fileDownloaded * 100) / (fileSize > 0 ? fileSize : 1));

                            String speedInfo = String.format(Locale.US,
                                    "%.1f MB/s • %.1f MB / %.1f MB",
                                    speedMBs,
                                    totalDownloaded / (1024f * 1024f),
                                    totalBytesFinal / (1024f * 1024f));

                            post(() -> listener.onProgress(percent, "Mengunduh " + pkgPrefix, speedInfo));
                        }
                    });

                    overallBytesDownloaded += pkg.size;
                    final long currentExtractedBytes = overallBytesDownloaded;
                    final int extractPercent = (int) ((currentExtractedBytes * 100) / (totalBytesFinal > 0 ? totalBytesFinal : 1));

                    // Ekstraksi otomatis jika file adalah arsip ZIP
                    if (downloadFile.getName().toLowerCase().endsWith(".zip")) {
                        post(() -> listener.onProgress(extractPercent, "Mengekstrak " + pkg.name + "…", "Ekstraksi"));

                        unzip(downloadFile, gameDir, extractedFile -> {
                            post(() -> listener.onProgress(extractPercent, "Mengekstrak: " + extractedFile, "Menyimpan ke disk"));
                        });

                        // Hapus file zip sementara setelah diekstrak untuk menghemat ruang memori HP
                        if (downloadFile.exists()) {
                            downloadFile.delete();
                        }
                    }

                    // Simpan versi paket ini secara lokal
                    prefs.edit().putInt(KEY_PKG_VER_PREFIX + pkg.id, pkg.version).apply();
                }

                // Tandai versi global selesai
                prefs.edit().putInt(KEY_GLOBAL_VER, manifest.version).apply();
                post(() -> listener.onInstalled(manifest));

            } catch (final Exception e) {
                Log.e(TAG, "Update gagal: " + e.getMessage(), e);
                post(() -> listener.onFailed(humanMessage(e)));
            }
        });
    }

    public void cancel() {
        cancelled.set(true);
    }

    public void shutdown() {
        cancelled.set(true);
        executor.shutdownNow();
    }

    // ==================================================================
    //  Internal: Network & File Ops
    // ==================================================================

    private interface SpeedCallback {
        void onProgress(long downloadedBytes, long totalBytes, float speedMBs);
    }

    private interface UnzipCallback {
        void onExtracting(String fileName);
    }

    private void fetchFileWithSpeed(String fileUrl, File destFile, long expectedSize, SpeedCallback callback) throws IOException {
        File tempFile = new File(destFile.getAbsolutePath() + ".part");
        HttpURLConnection conn = open(fileUrl);
        long downloaded = 0;
        long lastTime = System.currentTimeMillis();
        long lastBytes = 0;
        float currentSpeed = 0f;

        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " dari " + fileUrl);
            }

            long contentLength = conn.getContentLengthLong();
            if (contentLength <= 0) contentLength = expectedSize;

            try (InputStream in = new BufferedInputStream(conn.getInputStream(), 32 * 1024);
                 FileOutputStream out = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[32 * 1024];
                int read;

                while ((read = in.read(buffer)) != -1) {
                    if (cancelled.get()) {
                        throw new IOException("Dibatalkan pengguna");
                    }
                    out.write(buffer, 0, read);
                    downloaded += read;

                    long now = System.currentTimeMillis();
                    long timeDiff = now - lastTime;
                    if (timeDiff >= 400) { // Update speed setiap 400ms
                        long bytesDiff = downloaded - lastBytes;
                        currentSpeed = (bytesDiff / (timeDiff / 1000f)) / (1024f * 1024f);
                        lastTime = now;
                        lastBytes = downloaded;
                        if (callback != null) {
                            callback.onProgress(downloaded, contentLength, currentSpeed);
                        }
                    }
                }
                out.flush();
            }

            if (destFile.exists()) destFile.delete();
            if (!tempFile.renameTo(destFile)) {
                throw new IOException("Gagal memindahkan file part ke " + destFile.getName());
            }

        } finally {
            conn.disconnect();
            if (tempFile.exists() && cancelled.get()) {
                tempFile.delete();
            }
        }
    }

    private void unzip(File zipFile, File targetDir, UnzipCallback callback) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile), 32 * 1024))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (cancelled.get()) {
                    throw new IOException("Ekstraksi dibatalkan");
                }
                String entryName = ze.getName();
                if (entryName.startsWith("files/") || entryName.startsWith("files\")) {
                    entryName = entryName.substring(6);
                }
                if (entryName.trim().isEmpty()) {
                    zis.closeEntry();
                    continue;
                }
                File target = resolveSafe(targetDir, entryName);
                if (ze.isDirectory()) {
                    target.mkdirs();
                } else {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    if (callback != null) {
                        callback.onExtracting(ze.getName());
                    }
                    try (FileOutputStream fos = new FileOutputStream(target)) {
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

    private static File resolveSafe(File root, String relativePath) throws IOException {
        File target = new File(root, relativePath);
        String rootPath = root.getCanonicalPath();
        String targetPath = target.getCanonicalPath();
        if (!targetPath.startsWith(rootPath + File.separator) && !targetPath.equals(rootPath)) {
            throw new IOException("Security: Path traversal terdeteksi: " + relativePath);
        }
        return target;
    }

    private String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = open(urlStr);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " dari " + urlStr);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
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
        conn.setRequestProperty("User-Agent", "ViceSide-Updater/1.1");
        return conn;
    }

    private void post(Runnable r) {
        mainHandler.post(r);
    }

    private static String humanMessage(Throwable t) {
        String msg = t.getMessage();
        return (msg == null || msg.trim().isEmpty()) ? t.getClass().getSimpleName() : msg;
    }
}
