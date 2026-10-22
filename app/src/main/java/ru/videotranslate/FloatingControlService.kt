package ru.videotranslate

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast

class FloatingControlService : Service() {
    private var windowManager: WindowManager? = null
    private var button: TextView? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val palette = AppPrefs.palette(this)
        button = TextView(this).apply {
            text = if (AppPrefs.prefs(this@FloatingControlService).getBoolean(AppPrefs.IS_RUNNING, false)) "■" else "▶"
            textSize = 22f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(palette.accent)
                cornerRadius = 1000f
                setStroke(dp(2), Color.argb(100, 255, 255, 255))
            }
            elevation = dp(8).toFloat()
            setOnClickListener { toggle() }
            setOnLongClickListener {
                startActivity(Intent(this@FloatingControlService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                true
            }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(dp(58), dp(58), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12); y = dp(180)
        }
        try { windowManager?.addView(button, params) } catch (_: Exception) { stopSelf() }
    }

    private fun toggle() {
        val running = AppPrefs.prefs(this).getBoolean(AppPrefs.IS_RUNNING, false)
        if (running) {
            startService(Intent(this, TranslationService::class.java).setAction(TranslationService.ACTION_STOP))
            button?.text = "▶"
            Toast.makeText(this, "Перевод остановлен", Toast.LENGTH_SHORT).show()
        } else {
            val launch = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_START_FROM_OVERLAY, true)
            startActivity(launch)
            button?.text = "■"
            Toast.makeText(this, "Подтверди захват звука, если Android попросит", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        try { button?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        button = null
        windowManager = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
