package ru.videotranslate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var statusDot: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private val captureRequestCode = 102
    private val p: AppPrefs.Palette get() = AppPrefs.palette(this)
    private var renderedTheme: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
        if (intent.getBooleanExtra(EXTRA_START_FROM_OVERLAY, false)) {
            intent.removeExtra(EXTRA_START_FROM_OVERLAY)
            startButton.post { checkPermissionsAndStart() }
        }
    }

    private fun render() {
        renderedTheme = AppPrefs.theme(this)
        window.statusBarColor = p.background
        window.navigationBarColor = p.background
        window.decorView.systemUiVisibility = if (p.dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(p.background)
        }
        val brandRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        brandRow.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            contentDescription = "VideoTranslate"
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        })
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(TextView(this@MainActivity).apply {
                text = "VideoTranslate"; textSize = 22f
                typeface = Typeface.create("sans-serif", Typeface.BOLD); setTextColor(p.text)
            })
            addView(TextView(this@MainActivity).apply {
                text = "LIVE VOICE TRANSLATION"; textSize = 10f; letterSpacing = .12f; setTextColor(p.muted)
            })
        }
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(brandRow)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = rounded(p.surface, 24)
        }
        hero.addView(TextView(this).apply {
            text = "ENGLISH  →  РУССКИЙ"; textSize = 12f; letterSpacing = .08f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(p.accent)
        })
        hero.addView(TextView(this).apply {
            text = "Смотри видео.\nПонимай каждое слово."
            textSize = 28f; typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(p.text); setPadding(0, dp(10), 0, dp(8))
        })
        hero.addView(TextView(this).apply {
            text = "Захват звука · распознавание речи · перевод · русская озвучка"
            textSize = 14f; setTextColor(p.muted); setLineSpacing(dp(3).toFloat(), 1f)
        })
        root.addView(hero, params(20))

        root.addView(label("КАК ЭТО РАБОТАЕТ"), params(22))
        val pipeline = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(15), dp(10), dp(15)); background = rounded(p.surface, 18)
        }
        pipeline.addView(pipelineItem("01", "Слушает", p.accent), LinearLayout.LayoutParams(0, dp(66), 1f))
        pipeline.addView(TextView(this).apply { text = "›"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(p.muted) }, LinearLayout.LayoutParams(dp(18), dp(60)))
        pipeline.addView(pipelineItem("02", "Переводит", 0xFF27B58A.toInt()), LinearLayout.LayoutParams(0, dp(66), 1f))
        pipeline.addView(TextView(this).apply { text = "›"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(p.muted) }, LinearLayout.LayoutParams(dp(18), dp(60)))
        pipeline.addView(pipelineItem("03", "Озвучивает", 0xFFFFA94D.toInt()), LinearLayout.LayoutParams(0, dp(66), 1f))
        root.addView(pipeline, params(8))

        root.addView(label("УПРАВЛЕНИЕ ПЕРЕВОДОМ"), params(22))
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(13)); background = rounded(p.surface, 15)
        }
        statusDot = TextView(this).apply { text = "●"; textSize = 14f; setTextColor(0xFF27B58A.toInt()) }
        statusCard.addView(statusDot)
        status = TextView(this).apply {
            text = if (AppPrefs.prefs(this@MainActivity).getBoolean(AppPrefs.IS_RUNNING, false)) "  Перевод активен" else "  Готов к запуску"
            textSize = 14f; setTextColor(p.text)
        }
        statusCard.addView(status)
        root.addView(statusCard, params(8))

        startButton = Button(this).apply {
            text = "▶   Запустить перевод"; isAllCaps = false; textSize = 16f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); background = rounded(p.accent, 16)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { checkPermissionsAndStart() }
        }
        root.addView(startButton, params(12))
        stopButton = Button(this).apply {
            text = "■   Остановить перевод"; isAllCaps = false; textSize = 15f
            setTextColor(p.text); background = rounded(p.surface, 16)
            setOnClickListener {
                startService(Intent(this@MainActivity, TranslationService::class.java).setAction(TranslationService.ACTION_STOP))
                setStatus("Перевод остановлен", false)
            }
        }
        root.addView(stopButton, params(8))

        val secondary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        secondary.addView(Button(this).apply {
            text = "⚙  Настройки"; isAllCaps = false; setTextColor(p.text); background = rounded(p.surface, 14)
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })
        secondary.addView(Button(this).apply {
            text = "◉  Плавающая кнопка"; isAllCaps = false; setTextColor(p.text); background = rounded(p.surface, 14)
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                    Toast.makeText(this@MainActivity, "Разреши показ поверх окон, затем включи кнопку в настройках", Toast.LENGTH_LONG).show()
                } else {
                    AppPrefs.prefs(this@MainActivity).edit().putBoolean(AppPrefs.FLOATING_ENABLED, true).apply()
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(Intent(this@MainActivity, FloatingControlService::class.java))
                    else startService(Intent(this@MainActivity, FloatingControlService::class.java))
                    Toast.makeText(this@MainActivity, "Плавающая кнопка включена", Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(6) })
        root.addView(secondary, params(8))

        root.addView(label("ВАЖНО"), params(22))
        root.addView(TextView(this).apply {
            text = "На Android 10+ VideoTranslate пытается захватить внутренний звук. Если приложение-источник запрещает захват, используется микрофон. Чтобы слышать только русский голос, убавь громкость исходного видео. Перевод ИИ требует собственного API-ключа; Google ML Kit работает локально после загрузки модели."
            textSize = 13f; setTextColor(p.muted); setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }, params(2))
        root.addView(TextView(this).apply {
            text = "VideoTranslate  ·  preview"; textSize = 11f; gravity = Gravity.CENTER
            setTextColor(p.muted); setPadding(0, dp(18), 0, dp(4))
        }, params(10))

        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized && renderedTheme != AppPrefs.theme(this)) render()
        if (::status.isInitialized && AppPrefs.prefs(this).getBoolean(AppPrefs.IS_RUNNING, false)) setStatus("Перевод активен", true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START_FROM_OVERLAY, false)) {
            intent.removeExtra(EXTRA_START_FROM_OVERLAY)
            checkPermissionsAndStart()
        }
    }

    private fun pipelineItem(code: String, title: String, color: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        addView(TextView(this@MainActivity).apply {
            text = code; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(color); gravity = Gravity.CENTER
        })
        addView(TextView(this@MainActivity).apply {
            text = title; textSize = 11f; setTextColor(p.muted); gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0)
        })
    }

    private fun label(value: String) = TextView(this).apply {
        text = value; textSize = 11f; letterSpacing = .13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(p.muted)
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun params(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun setStatus(message: String, active: Boolean) {
        status.text = "  $message"
        statusDot.setTextColor(if (active) 0xFF27B58A.toInt() else p.muted)
        AppPrefs.prefs(this).edit().putBoolean(AppPrefs.IS_RUNNING, active).apply()
    }

    private fun checkPermissionsAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100); return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101); return
        }
        startTranslator()
    }

    private fun startTranslator() {
        if (AppPrefs.prefs(this).getString(AppPrefs.RECOGNITION_ENGINE, "whisper") == "google") {
            launchTranslationService(null, 0)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createScreenCaptureIntent(), captureRequestCode)
        } else launchTranslationService(null, 0)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != captureRequestCode) return
        if (resultCode == Activity.RESULT_OK && data != null) launchTranslationService(data, resultCode)
        else {
            setStatus("Захват звука не разрешён — запускаю микрофон", false)
            launchTranslationService(null, 0)
        }
    }

    private fun launchTranslationService(projectionData: Intent?, resultCode: Int) {
        val intent = Intent(this, TranslationService::class.java)
        if (projectionData != null) {
            intent.putExtra(TranslationService.EXTRA_RESULT_CODE, resultCode)
            intent.putExtra(TranslationService.EXTRA_RESULT_DATA, projectionData)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        setStatus(if (projectionData != null) "Захват внутреннего звука запущен" else "Перевод включён через микрофон", true)
        if (AppPrefs.prefs(this).getBoolean(AppPrefs.FLOATING_ENABLED, false)) {
            try { startService(Intent(this, FloatingControlService::class.java).setAction("ru.videotranslate.overlay.REFRESH")) } catch (_: Exception) {}
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (requestCode == 100 || requestCode == 101) checkPermissionsAndStart()
        } else setStatus("Разрешения не выданы", false)
    }

    companion object { const val EXTRA_START_FROM_OVERLAY = "ru.videotranslate.START_FROM_OVERLAY" }
}
