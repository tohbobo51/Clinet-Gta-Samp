package com.viceside.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DataUpdater(private val context: Context, private val manifestUrl: String) {

    companion object {
        private const val TAG = "DataUpdater"
        private const val BUFFER_SIZE = 64 * 1024
        private const val TIMEOUT_MS = 25000
    }

    data class PackageEntry(
        val id: String,
        val name: String,
        val version: Int,
        val size: Long,
        val urls: List<String>,
        val sha256: String,
        val gpu: String,
        val extractTo: String,
        val requiredFiles: List<String>
    )

    data class Manifest(
        val version: Int,
        val clientVersion: String,
        val minAppVersion: Int,
        val packages: List<PackageEntry>
    ) {
        companion object {
            fun parse(json: String): Manifest {
                val root = JSONObject(json)
                val version = root.optInt("version", 1)
                val clientVersion = root.optString("client_version", "1.0")
                val minAppVersion = root.optInt("min_app_version", 1)
                val pkgList = mutableListOf<PackageEntry>()

                val array = root.optJSONArray("packages")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val id = obj.optString("id", "pkg_$i")
                        val name = obj.optString("name", id)
                        val pkgVer = obj.optInt("version", 1)
                        val size = obj.optLong("size", 0L)
                        val sha = obj.optString("sha256", "").trim()
                        val gpu = obj.optString("gpu", "all").lowercase()
                        val extractTo = obj.optString("extract_to", "")

                        val urls = mutableListOf<String>()
                        if (obj.has("urls")) {
                            val uArr = obj.getJSONArray("urls")
                            for (u in 0 until uArr.length()) urls.add(uArr.getString(u))
                        } else if (obj.has("url")) {
                            urls.add(obj.getString("url"))
                        }

                        val reqFiles = mutableListOf<String>()
                        val reqArr = obj.optJSONArray("required_files")
                        if (reqArr != null) {
                            for (j in 0 until reqArr.length()) reqFiles.add(reqArr.getString(j))
                        }

                        pkgList.add(PackageEntry(id, name, pkgVer, size, urls, sha, gpu, extractTo, reqFiles))
                    }
                }
                return Manifest(version, clientVersion, minAppVersion, pkgList)
            }
        }
    }

    interface Listener {
        fun onChecking() {}
        fun onAppUpdateRequired(minVersion: Int) {}
        fun onComplete() {}
        fun onUpdateAvailable(manifest: Manifest, needed: List<PackageEntry>) {}
        fun onError(message: String) {}
        fun onProgress(
            percent: Float,
            downloadedBytes: Long,
            totalBytes: Long,
            speedBytesPerSec: Long,
            remainingSeconds: Long,
            currentFile: String,
            extractedFilesCount: Int,
            totalFilesEstimate: Int
        ) {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean(false)

    fun getGameDataDir(): File {
        val dir = context.getExternalFilesDir(null) ?: File(context.filesDir, "game_data")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isGameDataComplete(): Boolean {
        val gameDir = getGameDataDir()
        if (!gameDir.exists() || !gameDir.isDirectory) return false

        val coreFiles = listOf("anim/anim.img", "data/gta.dat", "texdb/samp.img")
        for (rel in coreFiles) {
            val f = File(gameDir, rel)
            if (!f.exists() || f.length() == 0L) return false
        }
        return true
    }

    fun check(listener: Listener) {
        if (executor.isShutdown) return
        listener.onChecking()

        executor.execute {
            try {
                val json = httpGet(manifestUrl)
                val manifest = Manifest.parse(json)

                // Check min app version
                val currentAppVersion = getAppVersionCode()
                if (manifest.minAppVersion > currentAppVersion) {
                    post { listener.onAppUpdateRequired(manifest.minAppVersion) }
                    return@execute
                }

                val needed = filterNeededPackages(manifest, GpuDetector.detectedGpu)
                if (needed.isEmpty() && isGameDataComplete()) {
                    post { listener.onComplete() }
                } else {
                    post { listener.onUpdateAvailable(manifest, needed) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
                post { listener.onError(e.message ?: "Gagal memeriksa pembaruan") }
            }
        }
    }

    fun filterNeededPackages(manifest: Manifest, gpu: String): List<PackageEntry> {
        val gameDir = getGameDataDir()
        val needed = mutableListOf<PackageEntry>()

        for (pkg in manifest.packages) {
            if (pkg.gpu != "all" && !pkg.gpu.equals(gpu, ignoreCase = true)) {
                continue
            }

            val markerFile = File(gameDir, ".${pkg.id}.installed")
            val isMarkerValid = markerFile.exists() && markerFile.readText().trim() == pkg.version.toString()

            var allFilesExist = true
            if (pkg.requiredFiles.isNotEmpty()) {
                for (rel in pkg.requiredFiles) {
                    val target = File(gameDir, rel)
                    if (!target.exists() || target.length() == 0L) {
                        allFilesExist = false
                        break
                    }
                }
            }

            if (!isMarkerValid || !allFilesExist) {
                needed.add(pkg)
            }
        }
        return needed
    }

    fun checkDiskSpace(needed: List<PackageEntry>): Boolean {
        val totalBytes = needed.sumOf { it.size }
        val stat = StatFs(getGameDataDir().path)
        val available = stat.availableBytes
        return available >= (totalBytes * 2.5).toLong()
    }

    fun download(manifest: Manifest, queue: List<PackageEntry>, listener: Listener) {
        cancelled.set(false)
        if (executor.isShutdown) {
            listener.onError("Service sedang ditutup")
            return
        }

        executor.execute {
            try {
                if (!checkDiskSpace(queue)) {
                    val totalMb = queue.sumOf { it.size } / (1024 * 1024)
                    throw IOException("Ruang penyimpanan tidak cukup. Dibutuhkan minimal ${ (totalMb * 2.5).toInt() } MB")
                }

                val gameDir = getGameDataDir()
                val totalBytes = queue.sumOf { it.size }
                var overallDownloadedBytes = 0L
                var totalExtractedFiles = 0

                for ((idx, pkg) in queue.withIndex()) {
                    if (cancelled.get()) throw IOException("Unduhan dibatalkan")

                    val zipFile = File(gameDir, "${pkg.id}.zip")
                    val partFile = File(gameDir, "${pkg.id}.zip.part")

                    var success = false
                    var lastError: Exception? = null

                    for (url in pkg.urls) {
                        try {
                            downloadFileWithResume(
                                url = url,
                                destFile = zipFile,
                                partFile = partFile,
                                expectedSize = pkg.size,
                                expectedSha = pkg.sha256,
                                currentBaseBytes = overallDownloadedBytes,
                                totalAllBytes = totalBytes,
                                currentPkgName = pkg.name,
                                listener = listener
                            )
                            success = true
                            break
                        } catch (e: Exception) {
                            if (cancelled.get()) throw e
                            Log.w(TAG, "Download mirror failed: $url: ${e.message}")
                            lastError = e
                        }
                    }

                    if (!success) {
                        throw lastError ?: IOException("Gagal mengunduh paket ${pkg.name}")
                    }

                    overallDownloadedBytes += pkg.size

                    // Atomic Extraction into temporary folder
                    val tmpExtractDir = File(gameDir, ".tmp_extract_${pkg.id}")
                    if (tmpExtractDir.exists()) tmpExtractDir.deleteRecursively()
                    tmpExtractDir.mkdirs()

                    unzipSafely(zipFile, tmpExtractDir) { fileEntryName ->
                        totalExtractedFiles++
                        val percent = if (totalBytes > 0) (overallDownloadedBytes * 100f / totalBytes) else 100f
                        post {
                            listener.onProgress(
                                percent = percent,
                                downloadedBytes = overallDownloadedBytes,
                                totalBytes = totalBytes,
                                speedBytesPerSec = 0L,
                                remainingSeconds = 0L,
                                currentFile = fileEntryName,
                                extractedFilesCount = totalExtractedFiles,
                                totalFilesEstimate = 7152
                            )
                        }
                    }

                    // Atomic Merge / Move to gameDir
                    moveDirectoryContents(tmpExtractDir, gameDir)
                    tmpExtractDir.deleteRecursively()
                    zipFile.delete()

                    // Write version marker
                    val marker = File(gameDir, ".${pkg.id}.installed")
                    marker.writeText(pkg.version.toString())
                }

                post { listener.onComplete() }

            } catch (e: Exception) {
                if (!cancelled.get()) {
                    Log.e(TAG, "Download error", e)
                    post { listener.onError(e.message ?: "Terjadi kesalahan saat mengunduh") }
                }
            }
        }
    }

    fun cancel() {
        cancelled.set(true)
    }

    fun shutdown() {
        cancelled.set(true)
        executor.shutdownNow()
    }

    // -------------------------------------------------------------------------
    // Network & Extraction Helpers
    // -------------------------------------------------------------------------

    private fun downloadFileWithResume(
        url: String,
        destFile: File,
        partFile: File,
        expectedSize: Long,
        expectedSha: String,
        currentBaseBytes: Long,
        totalAllBytes: Long,
        currentPkgName: String,
        listener: Listener
    ) {
        var existingBytes = if (partFile.exists()) partFile.length() else 0L
        if (existingBytes >= expectedSize && expectedSize > 0) {
            existingBytes = 0L
            partFile.delete()
        }

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ViceSide-Updater/1.1")
            if (existingBytes > 0) {
                setRequestProperty("Range", "bytes=$existingBytes-")
            }
        }

        val responseCode = conn.responseCode
        val isAppend = (responseCode == HttpURLConnection.HTTP_PARTIAL)
        if (!isAppend) {
            existingBytes = 0L
            if (partFile.exists()) partFile.delete()
        }

        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
            conn.disconnect()
            throw IOException("HTTP $responseCode dari $url")
        }

        var downloaded = existingBytes
        var lastTime = System.currentTimeMillis()
        var lastBytes = downloaded

        BufferedInputStream(conn.inputStream, BUFFER_SIZE).use { input ->
            FileOutputStream(partFile, isAppend).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    if (cancelled.get()) throw IOException("Dibatalkan")
                    output.write(buffer, 0, read)
                    downloaded += read

                    val now = System.currentTimeMillis()
                    val diffTime = now - lastTime
                    if (diffTime >= 400) {
                        val diffBytes = downloaded - lastBytes
                        val speed = (diffBytes * 1000L) / diffTime
                        lastTime = now
                        lastBytes = downloaded

                        val totalDone = currentBaseBytes + downloaded
                        val remainingBytes = (totalAllBytes - totalDone).coerceAtLeast(0L)
                        val remainingSec = if (speed > 0) remainingBytes / speed else 0L
                        val percent = if (totalAllBytes > 0) (totalDone * 100f / totalAllBytes) else 0f

                        post {
                            listener.onProgress(
                                percent = percent,
                                downloadedBytes = totalDone,
                                totalBytes = totalAllBytes,
                                speedBytesPerSec = speed,
                                remainingSeconds = remainingSec,
                                currentFile = currentPkgName,
                                extractedFilesCount = 0,
                                totalFilesEstimate = 7152
                            )
                        }
                    }
                }
                output.flush()
            }
        }

        conn.disconnect()

        // SHA-256 Verification
        if (expectedSha.isNotEmpty()) {
            val actualSha = calculateSha256(partFile)
            if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                partFile.delete()
                throw IOException("Checksum SHA-256 tidak cocok! File korup telah dihapus.")
            }
        }

        if (destFile.exists()) destFile.delete()
        if (!partFile.renameTo(destFile)) {
            throw IOException("Gagal mengubah part file ke ${destFile.name}")
        }
    }

    private fun unzipSafely(zipFile: File, targetDir: File, onFile: (String) -> Unit) {
        val buffer = ByteArray(BUFFER_SIZE)
        val canonicalRoot = targetDir.canonicalPath

        java.util.zip.ZipInputStream(BufferedInputStream(FileInputStream(zipFile), BUFFER_SIZE)).use { zis ->
            var ze: java.util.zip.ZipEntry?
            while (zis.nextEntry.also { ze = it } != null) {
                if (cancelled.get()) throw IOException("Ekstraksi dibatalkan")
                val entry = ze ?: break
                val target = File(targetDir, entry.name)

                // Zip-slip security protection
                if (!target.canonicalPath.startsWith(canonicalRoot + File.separator) && !target.canonicalPath.equals(canonicalRoot)) {
                    throw IOException("Security Violation: Path traversal di zip entry ${entry.name}")
                }

                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    onFile(target.name)
                    FileOutputStream(target).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                zis.closeEntry()
            }
        }
    }

    private fun moveDirectoryContents(source: File, dest: File) {
        if (!dest.exists()) dest.mkdirs()
        val files = source.listFiles() ?: return
        for (f in files) {
            val target = File(dest, f.name)
            if (f.isDirectory) {
                moveDirectoryContents(f, target)
                f.delete()
            } else {
                if (target.exists()) target.delete()
                if (!f.renameTo(target)) {
                    f.copyTo(target, overwrite = true)
                    f.delete()
                }
            }
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(BUFFER_SIZE)
            var read: Int
            while (fis.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ViceSide-Updater/1.1")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${conn.responseCode} dari $urlStr")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun getAppVersionCode(): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    private fun post(action: () -> Unit) {
        mainHandler.post(action)
    }
}
