package com.russia.game.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.ViewStub
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.os.Build
import androidx.constraintlayout.widget.ConstraintLayout
import com.viceside.core.CrashReporter
import com.russia.game.R
import com.russia.game.gui.hud.HudManager
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class Samp : GTASA() {
    private var mDialogClientSettings: DialogClientSettings? = null

    private external fun initSAMP(maxFps: Float, directory: String)

    
    override fun hideSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        } catch (ignored: Throwable) {}
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    private fun writeSampConfig() {
        try {
            val host = intent?.getStringExtra("server") ?: intent?.getStringExtra("ip") ?: "142.132.203.47"
            val port = intent?.getStringExtra("port") ?: intent?.getIntExtra("port", 10125)?.toString() ?: "10125"
            val nickname = intent?.getStringExtra("name") ?: intent?.getStringExtra("nick") ?: "ViceSide_Player"

            val targetDirs = listOfNotNull(filesDir, getExternalFilesDir(null))
            for (dir in targetDirs) {
                val sampFolder = File(dir, "SAMP")
                if (!sampFolder.exists()) sampFolder.mkdirs()
                val iniFile = File(sampFolder, "settings.ini")
                val content = "[client]\n" +
                        "ip=$host\n" +
                        "port=$port\n" +
                        "name=$nickname\n" +
                        "password=\n" +
                        "autologin=0\n" +
                        "server=0\n" +
                        "debug=0\n" +
                        "[gui]\n" +
                        "Font=visby-round-cf-extra-bold.ttf\n" +
                        "fps=60\n"
                iniFile.writeText(content)
                Log.d("SampConfig", "Wrote settings.ini to: " + iniFile.absolutePath)
            }
        } catch (e: Exception) {
            Log.w("SampConfig", "Failed to write settings.ini: " + e.message)
        }
    }

    override fun onCreate(bundle: Bundle?) {
        hideSystemUI()
        activity = this
        val display = Companion.windowManager.defaultDisplay
        maxFps = display.refreshRate
        val internalDir = File(filesDir, "AudioConfig")
        clearDir(internalDir)
        copyFromAssets(internalDir)
        writeSampConfig()
        initSAMP(maxFps, filesDir.toString())
        super.onCreate(bundle)
        init()
    }

    private fun clearDir(dir: File) {
        try {
            if (dir.exists()) {
                dir.deleteRecursively()
                Log.d("AudioConfig", "Audio directory cleared: ${dir.absolutePath}")
            }
        } catch (e: IOException) {
            Log.e("AudioConfig", "Failed to clear audio dir: ${e.message}")
        }
    }

    private fun copyFromAssets(dir: File) {
        try {
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val assetFiles = assets.list("AudioConfig") ?: return

            assetFiles.forEach { filename ->
                assets.open("AudioConfig/$filename").use { inputStream ->
                    File(dir, filename).outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                        Log.d("AudioConfig", "Copied audio: $filename → ${dir.absolutePath}")
                    }
                }
            }

        } catch (e: IOException) {
            Log.e("ShaderManager", "Failed to copy shaders: ${e.message}")
        }
    }

    fun init() {
        HudManager()

//        new Thread(() -> {
//            while (true) {
//        FrameLayout ui_layout = activity.findViewById(R.id.ui_layout);
//                int totalChildren = countAllChildren(ui_layout);
//                Log.d("df", "Total children count: " + totalChildren);
//
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        }).start();
    }

    fun countAllChildren(viewParent: ViewParent?): Int {
        if (viewParent == null || viewParent !is ViewGroup) {
            return 0
        }
        val childCount = viewParent.childCount
        var totalCount = childCount
        for (i in 0 until childCount) {
            val childView = viewParent.getChildAt(i)
            if (childView is ViewGroup) {
                totalCount += countAllChildren(childView)
            }
        }
        return totalCount
    }

    fun hideLoadingScreen() {
        val task: TimerTask = object : TimerTask() {
            override fun run() {
                activity.runOnUiThread {
                    val loadscreen_main_layout = activity.findViewById<ConstraintLayout>(R.id.loadscreen_main_layout)
                    val parentContainer = loadscreen_main_layout.parent as ViewGroup
                    parentContainer.removeView(loadscreen_main_layout)
                }
            }
        }
        val timer = Timer("Timer")
        timer.schedule(task, 900L)
    }

    fun exitGame() {
        CrashReporter.deleteUnsentReports()
        CrashReporter.setCrashlyticsCollectionEnabled(false)
        finishAndRemoveTask()
        System.exit(0)
    }

    fun goVibrate(milliseconds: Int) {
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(milliseconds.toLong())
        }
    }

    private fun openUrl(url: String) {
        runOnUiThread {
            val address = Uri.parse(url)
            val openlink = Intent(Intent.ACTION_VIEW, address)
            startActivity(openlink)
        }
    }

    private fun copyTextToBuffer(string: String) {
        runOnUiThread {
            val clipboardManager = this.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("text", string)
            clipboardManager.setPrimaryClip(clipData)

            Toast.makeText(this, "Скопированно в буфер обмена ", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        @JvmStatic
        lateinit var activity: AppCompatActivity

        @JvmStatic
        val vibrator by lazy { activity.getSystemService(VIBRATOR_SERVICE) as Vibrator }

        @JvmStatic
        var maxFps = 0f

        val windowManager by lazy { activity.getSystemService(WINDOW_SERVICE) as WindowManager }

        val formatter = DecimalFormatSymbols(Locale.getDefault()).run {
            groupingSeparator = '.'
            DecimalFormat("###,###.###", this)
        }

        const val INVALID_PLAYER_ID = 65535

        val soundPool = SoundPool.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()

        @JvmStatic
        val clickAnim by lazy { AnimationUtils.loadAnimation(activity, R.anim.button_click) }

        // func
        external fun sendCommand(command: String?)
        external fun playUrlSound(url: String?)
        external fun gameFilesChecked(ok: Boolean)

        fun deInflatedViewStud(inflated: View?, id: Int, layout: Int) {
            activity.runOnUiThread {
                if (inflated == null) {
                    return@runOnUiThread
                }
                val parentContainer = inflated.parent as ViewGroup ?: return@runOnUiThread
                val index = parentContainer.indexOfChild(inflated)
                if (index == -1) {
                    return@runOnUiThread
                }
                parentContainer.removeViewAt(index)
                val viewStub = ViewStub(activity)
                viewStub.layoutResource = layout
                viewStub.id = id
                parentContainer.addView(viewStub, index)
            }
        }

        @JvmStatic
        private fun requestGameFilesCheck() {
            Thread {
                activity.runOnUiThread { gameFilesChecked(true) }
            }.start()
        }

        @JvmStatic
        private fun getSignature(): String {
            var apkSignature: String? = null
            try {
                val packageInfo = activity.packageManager.getPackageInfo(
                    activity.packageName,
                    PackageManager.GET_SIGNATURES
                )
                for (signature in packageInfo.signatures!!) {
                    val md = MessageDigest.getInstance("SHA")
                    md.update(signature.toByteArray())
                    apkSignature = Base64.encodeToString(md.digest(), Base64.DEFAULT)
                }
            } catch (ignored: Exception) {
            }
            return apkSignature.toString()
        }

        val isTablet: Boolean
            get() = activity.resources.getBoolean(R.bool.is_tablet)
    }

    fun showClientSettings() {
        runOnUiThread {
            if (mDialogClientSettings != null) {
                mDialogClientSettings = null
            }
            mDialogClientSettings = DialogClientSettings()
            mDialogClientSettings?.show(supportFragmentManager, "test")
        }
    }
}
