package com.viceside.launcher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.russia.game.R
import com.russia.game.core.Samp
import java.io.File
import java.util.Locale

class LauncherActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ViceSideLauncher"
        private const val PREFS_SETTINGS = "vice_settings"
        private const val KEY_MUTE_MUSIC = "mute_music"
        private const val KEY_MUSIC_VOLUME = "music_volume"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_SAVED_NICKNAME = "saved_nickname"
        private const val DEFAULT_SERVER_IP = "15.235.175.76"
        private const val DEFAULT_SERVER_PORT = "7005"
        private const val MANIFEST_URL = "https://raw.githubusercontent.com/tohbobo51/Clinet-Gta-Samp/main/version.json"
    }

    // Containers
    private lateinit var splashContainer: FrameLayout
    private lateinit var mainContent: ConstraintLayout
    private lateinit var downloadContainer: ConstraintLayout

    // Splash views
    private lateinit var progressLoadingHorizontal: ProgressBar
    private lateinit var tvSplashFileStatus: TextView

    // Main views
    private lateinit var tvWelcomeSubtitle: TextView
    private lateinit var accountBox: LinearLayout
    private lateinit var tvAvatarInitial: TextView
    private lateinit var tvAccountBadge: TextView
    private lateinit var tvAccountName: TextView
    private lateinit var tvAccountEmail: TextView
    private lateinit var btnLogout: Button
    private lateinit var btnGoogleSignIn: Button
    private lateinit var btnMain: Button
    private lateinit var btnSettings: Button
    private lateinit var serverInfoContainer: LinearLayout
    private lateinit var settingsContainer: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var swMuteMusic: SwitchCompat
    private lateinit var sbMusicVolume: SeekBar
    private lateinit var swReduceMotion: SwitchCompat

    // Download views (Screenshot 2)
    private lateinit var tvCardPercent: TextView
    private lateinit var tvCardFilesCount: TextView
    private lateinit var progressCardDownload: ProgressBar
    private lateinit var btnCardStartDownload: Button
    private lateinit var tvCardDownloadedMb: TextView
    private lateinit var tvCardSpeed: TextView
    private lateinit var tvCardRemaining: TextView
    private lateinit var tvCardTotalSize: TextView
    private lateinit var tvCardCurrentFile: TextView
    private lateinit var tvCardTip: TextView

    // Core managers
    private lateinit var dataUpdater: DataUpdater
    private lateinit var prefs: SharedPreferences
    private var bgmPlayer: MediaPlayer? = null
    private var currentVolume = 0.70f
    private var isMuted = false
    private var cachedManifest: DataUpdater.Manifest? = null
    private var cachedNeeded: List<DataUpdater.PackageEntry> = emptyList()

    private val tips = listOf(
        "Kunci kendaraanmu dengan U agar tidak dicuri pemain lain.",
        "Gunakan sabuk pengaman agar tidak terlempar saat tabrakan.",
        "Bekerjalah sebagai kurir atau penambang untuk mengumpulkan uang awal.",
        "Taati rambu lalu lintas untuk menghindari denda polisi.",
        "Beli ponsel di toko terdekat untuk dapat berkomunikasi dengan pemain lain."
    )

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(Exception::class.java)
            if (account != null) {
                onGoogleSignInSuccess(account)
            } else {
                Toast.makeText(this, "Login Google gagal", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google sign-in exception", e)
            Toast.makeText(this, "Login Google dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Fullscreen Cutout Mode (Android 9+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_launcher)

        initViews()
        setupImmersiveMode()
        loadPreferences()
        setupSettingsControls()
        setupButtons()

        dataUpdater = DataUpdater(this, MANIFEST_URL)

        // Start BGM
        initAudio()

        // Detect GPU & Check game data files
        GpuDetector.detect(this) { gpu ->
            Log.d(TAG, "Detected GPU: $gpu")
            checkGameData()
        }
    }

    private fun initViews() {
        splashContainer = findViewById(R.id.splashContainer)
        mainContent = findViewById(R.id.mainContent)
        downloadContainer = findViewById(R.id.downloadContainer)

        progressLoadingHorizontal = findViewById(R.id.progressLoadingHorizontal)
        tvSplashFileStatus = findViewById(R.id.tvSplashFileStatus)

        tvWelcomeSubtitle = findViewById(R.id.tvWelcomeSubtitle)
        accountBox = findViewById(R.id.accountBox)
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial)
        tvAccountBadge = findViewById(R.id.tvAccountBadge)
        tvAccountName = findViewById(R.id.tvAccountName)
        tvAccountEmail = findViewById(R.id.tvAccountEmail)
        btnLogout = findViewById(R.id.btnLogout)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        btnMain = findViewById(R.id.btnMain)
        btnSettings = findViewById(R.id.btnSettings)
        serverInfoContainer = findViewById(R.id.serverInfoContainer)
        settingsContainer = findViewById(R.id.settingsContainer)
        tvStatus = findViewById(R.id.tvStatus)
        swMuteMusic = findViewById(R.id.swMuteMusic)
        sbMusicVolume = findViewById(R.id.sbMusicVolume)
        swReduceMotion = findViewById(R.id.swReduceMotion)

        tvCardPercent = findViewById(R.id.tvCardPercent)
        tvCardFilesCount = findViewById(R.id.tvCardFilesCount)
        progressCardDownload = findViewById(R.id.progressCardDownload)
        btnCardStartDownload = findViewById(R.id.btnCardStartDownload)
        tvCardDownloadedMb = findViewById(R.id.tvCardDownloadedMb)
        tvCardSpeed = findViewById(R.id.tvCardSpeed)
        tvCardRemaining = findViewById(R.id.tvCardRemaining)
        tvCardTotalSize = findViewById(R.id.tvCardTotalSize)
        tvCardCurrentFile = findViewById(R.id.tvCardCurrentFile)
        tvCardTip = findViewById(R.id.tvCardTip)

        // Initial view states
        splashContainer.visibility = View.VISIBLE
        downloadContainer.visibility = View.GONE
        mainContent.visibility = View.GONE
        mainContent.alpha = 0f
    }

    private fun setupImmersiveMode() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupImmersiveMode()
    }

    private fun loadPreferences() {
        prefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        isMuted = prefs.getBoolean(KEY_MUTE_MUSIC, false)
        val volInt = prefs.getInt(KEY_MUSIC_VOLUME, 70)
        currentVolume = volInt / 100f

        swMuteMusic.isChecked = isMuted
        sbMusicVolume.progress = volInt
        swReduceMotion.isChecked = prefs.getBoolean(KEY_REDUCE_MOTION, false)

        val lastGoogleAccount = GoogleSignIn.getLastSignedInAccount(this)
        if (lastGoogleAccount != null) {
            onGoogleSignInSuccess(lastGoogleAccount)
        } else {
            showGuestUi()
        }
    }

    private fun setupSettingsControls() {
        swMuteMusic.setOnCheckedChangeListener { _, isChecked ->
            isMuted = isChecked
            prefs.edit().putBoolean(KEY_MUTE_MUSIC, isChecked).apply()
            updateBgmVolume()
        }

        sbMusicVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentVolume = progress / 100f
                    prefs.edit().putInt(KEY_MUSIC_VOLUME, progress).apply()
                    updateBgmVolume()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        swReduceMotion.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_REDUCE_MOTION, isChecked).apply()
        }
    }

    private fun setupButtons() {
        btnSettings.setOnClickListener {
            if (settingsContainer.visibility == View.VISIBLE) {
                settingsContainer.visibility = View.GONE
                serverInfoContainer.visibility = View.VISIBLE
            } else {
                serverInfoContainer.visibility = View.GONE
                settingsContainer.visibility = View.VISIBLE
            }
        }

        btnGoogleSignIn.setOnClickListener {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
            val client = GoogleSignIn.getClient(this, gso)
            signInLauncher.launch(client.signInIntent)
        }

        btnLogout.setOnClickListener {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener {
                showGuestUi()
                Toast.makeText(this, "Anda telah logout", Toast.LENGTH_SHORT).show()
            }
        }

        // CUMA SATU TOMBOL DOWNLOAD, TANPA BATAL (Screenshot 2)
        btnCardStartDownload.setOnClickListener {
            startDownloadProcess()
        }

        // Tombol PLAY meluncurkan Samp.kt langsung
        btnMain.setOnClickListener {
            if (!dataUpdater.isGameDataComplete()) {
                showDownloadScreen()
            } else {
                launchGameClient()
            }
        }
    }

    private fun checkGameData() {
        tvSplashFileStatus.text = "Memeriksa kelengkapan data game..."
        progressLoadingHorizontal.progress = 30

        dataUpdater.check(object : DataUpdater.Listener {
            override fun onChecking() {
                tvSplashFileStatus.text = "Menghubungkan ke server pembaruan..."
            }

            override fun onAppUpdateRequired(minVersion: Int) {
                AlertDialog.Builder(this@LauncherActivity)
                    .setTitle("Pembaruan Diperlukan")
                    .setMessage("Versi launcher ini terlalu lama. Silakan perbarui aplikasi ke versi terbaru.")
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            }

            override fun onComplete() {
                // Data sudah lengkap! Masuk ke layar utama
                progressLoadingHorizontal.progress = 100
                tvSplashFileStatus.text = "Data game lengkap!"
                Handler(Looper.getMainLooper()).postDelayed({
                    showMainScreen()
                }, 600)
            }

            override fun onUpdateAvailable(manifest: DataUpdater.Manifest, needed: List<DataUpdater.PackageEntry>) {
                // Data BELUM lengkap: Alihkan ke layar download persis Screenshot 2
                cachedManifest = manifest
                cachedNeeded = needed
                progressLoadingHorizontal.progress = 100
                tvSplashFileStatus.text = "Pembaruan ditemukan"
                Handler(Looper.getMainLooper()).postDelayed({
                    showDownloadScreen()
                    // Otomatis mulai unduh data
                    startDownloadProcess()
                }, 400)
            }

            override fun onError(message: String) {
                Log.w(TAG, "Check error: $message")
                // Jika offline tapi data lokal sudah lengkap, izinkan bermain
                if (dataUpdater.isGameDataComplete()) {
                    showMainScreen()
                } else {
                    tvSplashFileStatus.text = "Pemeriksaan gagal: $message"
                    Toast.makeText(this@LauncherActivity, message, Toast.LENGTH_LONG).show()
                    showDownloadScreen()
                }
            }
        })
    }

    private fun showDownloadScreen() {
        splashContainer.visibility = View.GONE
        mainContent.visibility = View.GONE
        downloadContainer.visibility = View.VISIBLE

        tvCardTip.text = tips.random()
        val totalBytes = cachedNeeded.sumOf { it.size }
        val totalGb = String.format(Locale.US, "%.2f GB", totalBytes / (1024f * 1024f * 1024f))
        tvCardTotalSize.text = totalGb
        tvCardDownloadedMb.text = "0.0 MB"
        tvCardSpeed.text = "0 KB/s"
        tvCardRemaining.text = "--:--:--"
        tvCardPercent.text = "0.0%"
        progressCardDownload.progress = 0
    }

    private fun startDownloadProcess() {
        val manifest = cachedManifest ?: return
        val queue = cachedNeeded.ifEmpty { manifest.packages }

        btnCardStartDownload.isEnabled = false
        btnCardStartDownload.text = "MENGUNDUH DATA..."

        dataUpdater.download(manifest, queue, object : DataUpdater.Listener {
            override fun onProgress(
                percent: Float,
                downloadedBytes: Long,
                totalBytes: Long,
                speedBytesPerSec: Long,
                remainingSeconds: Long,
                currentFile: String,
                extractedFilesCount: Int,
                totalFilesEstimate: Int
            ) {
                tvCardPercent.text = String.format(Locale.US, "%.1f%%", percent)
                progressCardDownload.progress = percent.toInt()

                val downloadedMb = String.format(Locale.US, "%.1f MB", downloadedBytes / (1024f * 1024f))
                tvCardDownloadedMb.text = downloadedMb

                val speedStr = if (speedBytesPerSec >= 1024 * 1024) {
                    String.format(Locale.US, "%.1f MB/s", speedBytesPerSec / (1024f * 1024f))
                } else {
                    String.format(Locale.US, "%d KB/s", speedBytesPerSec / 1024)
                }
                tvCardSpeed.text = speedStr

                val hours = remainingSeconds / 3600
                val minutes = (remainingSeconds % 3600) / 60
                val seconds = remainingSeconds % 60
                tvCardRemaining.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

                val filesText = if (extractedFilesCount > 0) {
                    "$extractedFilesCount / $totalFilesEstimate files"
                } else {
                    "${(percent * 71.52).toInt()} / 7152 files"
                }
                tvCardFilesCount.text = filesText
                tvCardCurrentFile.text = currentFile
            }

            override fun onComplete() {
                Toast.makeText(this@LauncherActivity, "Data game berhasil diunduh & siap dimainkan!", Toast.LENGTH_SHORT).show()
                showMainScreen()
            }

            override fun onError(message: String) {
                btnCardStartDownload.isEnabled = true
                btnCardStartDownload.text = "COBA LAGI"
                Toast.makeText(this@LauncherActivity, message, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun showMainScreen() {
        splashContainer.visibility = View.GONE
        downloadContainer.visibility = View.GONE
        mainContent.visibility = View.VISIBLE
        mainContent.animate().alpha(1f).setDuration(400).start()
        tvStatus.text = "Ready"
        btnMain.isEnabled = true
    }

    private fun showGuestUi() {
        accountBox.visibility = View.GONE
        btnGoogleSignIn.visibility = View.VISIBLE
        tvWelcomeSubtitle.text = "Silakan login menggunakan Google untuk mulai bermain."
    }

    private fun onGoogleSignInSuccess(account: GoogleSignInAccount) {
        btnGoogleSignIn.visibility = View.GONE
        accountBox.visibility = View.VISIBLE

        val name = account.displayName ?: "Player"
        tvWelcomeSubtitle.text = "Welcome back, $name!"
        tvAccountName.text = name
        tvAccountEmail.text = account.email ?: ""
        tvAvatarInitial.text = name.take(1).uppercase()
        tvAccountBadge.text = "AKUN GOOGLE TERHUBUNG"

        prefs.edit().putString(KEY_SAVED_NICKNAME, name.replace(" ", "_")).apply()
    }

    private fun launchGameClient() {
        val serverIp = DEFAULT_SERVER_IP
        val serverPort = DEFAULT_SERVER_PORT
        val nickname = prefs.getString(KEY_SAVED_NICKNAME, "ViceSide_Player") ?: "ViceSide_Player"

        // 1. Tulis settings.ini langsung ke folder internal data game (Langkah 4)
        val extFiles = getExternalFilesDir(null)
        if (extFiles != null) {
            val sampDir = File(extFiles, "SAMP")
            if (!sampDir.exists()) sampDir.mkdirs()
            val ini = File(sampDir, "settings.ini")
            val content = "[client]\n" +
                "ip=$serverIp\n" +
                "port=$serverPort\n" +
                "name=$nickname\n" +
                "password=\n" +
                "autologin=0\n" +
                "server=0\n" +
                "debug=0\n" +
                "[gui]\n" +
                "Font=visby-round-cf-extra-bold.ttf\n" +
                "fps=60\n"
            ini.writeText(content)
            Log.d(TAG, "Settings.ini written to: ${ini.absolutePath}")
            Log.d(TAG, "Path verification: getExternalFilesDir = ${extFiles.absolutePath}")
        }

        // 2. Luncurkan Activity Samp native dalam APK yang sama
        val intent = Intent(this, Samp::class.java).apply {
            putExtra("server", serverIp)
            putExtra("ip", serverIp)
            putExtra("port", serverPort)
            putExtra("name", nickname)
            putExtra("nick", nickname)
        }
        startActivity(intent)
        finish()
    }

    private fun initAudio() {
        try {
            // Gunakan musik lokal jika ada di res/raw
            val resId = resources.getIdentifier("bgm_synthwave", "raw", packageName)
            if (resId != 0) {
                bgmPlayer = MediaPlayer.create(this, resId).apply {
                    isLooping = true
                }
                updateBgmVolume()
                if (!isMuted) bgmPlayer?.start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio init warning: ${e.message}")
        }
    }

    private fun updateBgmVolume() {
        val vol = if (isMuted) 0f else currentVolume
        bgmPlayer?.setVolume(vol, vol)
    }

    override fun onDestroy() {
        dataUpdater.shutdown()
        bgmPlayer?.release()
        bgmPlayer = null
        super.onDestroy()
    }
}
