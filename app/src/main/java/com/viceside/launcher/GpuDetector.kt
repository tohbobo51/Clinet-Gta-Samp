package com.viceside.launcher

import android.opengl.GLES10
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Mendeteksi tipe kompresi tekstur GPU perangkat: DXT (Adreno), ETC (Mali), atau PVR (PowerVR).
 */
object GpuDetector {
    private const val TAG = "GpuDetector"

    var detectedGpu: String = "etc" // Default safe fallback
        private set

    fun detect(activity: AppCompatActivity, onDetected: (String) -> Unit) {
        val glView = GLSurfaceView(activity)
        glView.setRenderer(object : GLSurfaceView.Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                val renderer = GLES10.glGetString(GLES10.GL_RENDERER)?.lowercase() ?: ""
                val extensions = GLES10.glGetString(GLES10.GL_EXTENSIONS)?.lowercase() ?: ""

                val gpu = when {
                    renderer.contains("adreno") || extensions.contains("texture_compression_s3tc") -> "dxt"
                    renderer.contains("powervr") || extensions.contains("texture_compression_pvrtc") -> "pvr"
                    renderer.contains("mali") -> "etc"
                    else -> "etc"
                }

                Log.d(TAG, "Renderer: $renderer, GPU Type: $gpu")
                detectedGpu = gpu

                Handler(Looper.getMainLooper()).post {
                    (glView.parent as? ViewGroup)?.removeView(glView)
                    onDetected(gpu)
                }
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {}
            override fun onDrawFrame(gl: GL10?) {}
        })

        activity.addContentView(
            glView,
            ViewGroup.LayoutParams(1, 1)
        )
    }
}
