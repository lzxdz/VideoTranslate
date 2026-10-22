package ru.videotranslate

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*

class FloatingControlService : Service() {
    private var windowManager: WindowManager? = null
    private var panel: LinearLayout? = null
    private var title: TextView? = null
    private var toggleButton: Button? = null
    private var volumeLabel: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(OVERLAY_NOTIFICATION_ID, notification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= 26) startForeground(OVERLAY_NOTIFICATION_ID, notification())
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        buildPanel()
    }

    private fun buildPanel() {
        val palette = AppPrefs.palette(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(palette.surface)
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.argb(110, palette.accent))
            }
            elevation = dp(12).toFloat()
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        title = TextView(this).apply {
            text = "VideoTranslate"; textSize = 14f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(palette.text)
        }
        top.addView(title, LinearLayout.LayoutParams(0, dp(38), 1f))
        val close = TextView(this).apply {
            text = "×"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(palette.muted)
            setOnClickListener {
                AppPrefs.prefs(this@FloatingControlService).edit().putBoolean(AppPrefs.FLOATING_ENABLED, false).apply()
                stopSelf()
            }
        }
        top.addView(close, LinearLayout.LayoutParams(dp(36), dp(38)))
        card.addView(top)
        top.setOnTouchListener(DragTouchListener())

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        toggleButton = Button(this).apply {
            isAllCaps = false; textSize = 13f
            setTextColor(Color.WHITE)
            background = rounded(palette.accent, 13)
            setOnClickListener { toggleTranslation() }
        }
        controls.addView(toggleButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })
        val open = Button(this).apply {
            text = "Открыть"; isAllCaps = false; textSize = 12f
            setTextColor(palette.text); background = rounded(palette.background, 13)
            setOnClickListener {
                startActivity(Intent(this@FloatingControlService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }
        controls.addView(open, LinearLayout.LayoutParams(dp(78), dp(46)))
        card.addView(controls)

        val volumeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        volumeRow.addView(TextView(this).apply {
            text = "Голос"; textSize = 12f; setTextColor(palette.muted)
        }, LinearLayout.LayoutParams(dp(48), -2))
        val seek = SeekBar(this).apply {
            max = 100
            progress = (AppPrefs.prefs(this@FloatingControlService).getFloat(AppPrefs.TTS_VOLUME, 1f) * 100).toInt().coerceIn(0, 100)
            progressTintList = android.content.res.ColorStateList.valueOf(palette.accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(palette.accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) AppPrefs.prefs(this@FloatingControlService).edit().putFloat(AppPrefs.TTS_VOLUME, progress / 100f).apply()
                    volumeLabel?.text = "$progress%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        volumeRow.addView(seek, LinearLayout.LayoutParams(0, dp(38), 1f))
        volumeLabel = TextView(this).apply {
            text = "${seek.progress}%"; textSize = 11f; setTextColor(palette.text); gravity = Gravity.END
        }
        volumeRow.addView(volumeLabel, LinearLayout.LayoutParams(dp(36), -2))
        card.addView(volumeRow)
        card.addView(TextView(this).apply {
            text = "Переведённый голос"; textSize = 10f; setTextColor(palette.muted)
        })

        panel = card
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(dp(270), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12); y = dp(170)
        }
        try {
            windowManager?.addView(card, params)
            refreshState()
        } catch (_: Exception) { stopSelf() }
    }

    private fun toggleTranslation() {
        val running = AppPrefs.prefs(this).getBoolean(AppPrefs.IS_RUNNING, false)
        if (running) {
            startService(Intent(this, TranslationService::class.java).setAction(TranslationService.ACTION_STOP))
            AppPrefs.prefs(this).edit().putBoolean(AppPrefs.IS_RUNNING, false).apply()
            refreshState()
            Toast.makeText(this, "Перевод остановлен", Toast.LENGTH_SHORT).show()
        } else {
            val launch = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_START_FROM_OVERLAY, true)
            startActivity(launch)
            Toast.makeText(this, "Подтверди захват звука, если Android попросит", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshState() {
        val running = AppPrefs.prefs(this).getBoolean(AppPrefs.IS_RUNNING, false)
        title?.text = if (running) "VideoTranslate · активно" else "VideoTranslate"
        toggleButton?.text = if (running) "■  Остановить" else "▶  Запустить"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshState()
        return START_STICKY
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = params ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y; touchX = event.rawX; touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (startX + touchX - event.rawX).toInt().coerceAtLeast(0)
                    lp.y = (startY + event.rawY - touchY).toInt().coerceAtLeast(0)
                    try { windowManager?.updateViewLayout(panel, lp) } catch (_: Exception) {}
                    return true
                }
            }
            return false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(OVERLAY_CHANNEL, "Плавающая панель", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, OVERLAY_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("VideoTranslate")
            .setContentText("Плавающая панель управления активна").setOngoing(true).setContentIntent(open).build()
        else @Suppress("DEPRECATION") Notification.Builder(this)
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("VideoTranslate")
            .setContentText("Плавающая панель управления активна").setOngoing(true).setContentIntent(open).build()
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    override fun onDestroy() {
        try { panel?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        panel = null; windowManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    companion object {
        private const val OVERLAY_CHANNEL = "video_translate_overlay"
        private const val OVERLAY_NOTIFICATION_ID = 43
    }
}
