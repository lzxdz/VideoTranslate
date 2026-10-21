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
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var statusDot: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val bg = Color.rgb(10, 15, 28)
    private val panel = Color.rgb(20, 29, 48)
    private val textPrimary = Color.rgb(242, 246, 255)
    private val textMuted = Color.rgb(157, 172, 198)
    private val accent = Color.rgb(113, 100, 255)
    private val mint = Color.rgb(64, 220, 177)
    private val captureRequestCode = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(28))
            setBackgroundColor(bg)
        }

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = TextView(this).apply {
            text = "▶"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(accent, 16)
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
        }
        brandRow.addView(logo)
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, 0, 0)
            addView(TextView(this@MainActivity).apply {
                text = "VideoTranslate"
                textSize = 23f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                setTextColor(textPrimary)
            })
            addView(TextView(this@MainActivity).apply {
                text = "ЛИЧНЫЙ ПЕРЕВОДЧИК ВИДЕО"
                textSize = 10f
                letterSpacing = 0.12f
                setTextColor(textMuted)
            })
        }
        brandRow.addView(brandText)
        root.addView(brandRow)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = rounded(panel, 22)
        }
        val eyebrow = TextView(this).apply {
            text = "ENGLISH  →  РУССКИЙ"
            textSize = 12f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mint)
        }
        hero.addView(eyebrow)
        hero.addView(TextView(this).apply {
            text = "Слушай. Понимай.\nСмотри дальше."
            textSize = 28f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(textPrimary)
            setPadding(0, dp(10), 0, dp(8))
        })
        hero.addView(TextView(this).apply {
            text = "Английская речь → русский перевод и озвучка. Модели работают на устройстве после первой загрузки."
            textSize = 14f
            setTextColor(textMuted)
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        root.addView(hero, marginParams(top = 22))

        root.addView(sectionLabel("ПЕРЕВОД"), marginParams(top = 22))
        val pipeline = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(15), dp(12), dp(15))
            background = rounded(panel, 18)
        }
        pipeline.addView(pipelineItem("EN", "Речь", accent), LinearLayout.LayoutParams(0, dp(60), 1f))
        pipeline.addView(TextView(this).apply {
            text = "→"
            textSize = 20f
            setTextColor(textMuted)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(24), dp(60)))
        pipeline.addView(pipelineItem("RU", "Перевод", mint), LinearLayout.LayoutParams(0, dp(60), 1f))
        pipeline.addView(TextView(this).apply {
            text = "→"
            textSize = 20f
            setTextColor(textMuted)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(24), dp(60)))
        pipeline.addView(pipelineItem("♫", "Озвучка", Color.rgb(255, 190, 105)), LinearLayout.LayoutParams(0, dp(60), 1f))
        root.addView(pipeline, marginParams(top = 8))

        root.addView(sectionLabel("УПРАВЛЕНИЕ"), marginParams(top = 22))
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(13))
            background = rounded(Color.rgb(15, 39, 43), 15)
        }
        statusDot = TextView(this).apply {
            text = "●"
            textSize = 14f
            setTextColor(mint)
        }
        statusCard.addView(statusDot)
        status = TextView(this).apply {
            text = "  Готов к запуску"
            textSize = 14f
            setTextColor(Color.rgb(190, 245, 227))
        }
        statusCard.addView(status)
        root.addView(statusCard, marginParams(top = 8))

        startButton = Button(this).apply {
            text = "▶    Запустить перевод"
            isAllCaps = false
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(accent, 16)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { checkPermissionsAndStart() }
        }
        root.addView(startButton, marginParams(top = 12))
        stopButton = Button(this).apply {
            text = "Остановить перевод"
            isAllCaps = false
            textSize = 15f
            setTextColor(textPrimary)
            background = rounded(Color.rgb(37, 47, 68), 16)
            setOnClickListener {
                stopService(Intent(this@MainActivity, TranslationService::class.java))
                setStatus("Перевод остановлен", false)
            }
        }
        root.addView(stopButton, marginParams(top = 8))

        root.addView(sectionLabel("ВАЖНО ЗНАТЬ"), marginParams(top = 22))
        val privacy = TextView(this).apply {
            text = "🔒  История переводов не сохраняется.\n\n🔊  На Android 10+ приложение пытается захватить внутренний звук видео. Если источник запрещает захват, используется микрофон.\n\n🎧  Приложение озвучивает только русский перевод, но Android не разрешает надёжно заглушить звук другого приложения. Чтобы слышать только перевод, убавь громкость исходного видео или медиагромкость.\n\n⚡  Используется компактная модель Whisper tiny.en. При первом запуске нужно скачать модели через интернет."
            textSize = 13f
            setTextColor(textMuted)
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(privacy, marginParams(top = 2))
        root.addView(TextView(this).apply {
            text = "VideoTranslate • тестовая версия"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(99, 115, 142))
            setPadding(0, dp(18), 0, dp(4))
        }, marginParams(top = 10))

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
            addView(root)
        })
    }

    private fun pipelineItem(code: String, title: String, color: Int): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(TextView(this@MainActivity).apply {
                text = code
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 11f
                setTextColor(textMuted)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun sectionLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        letterSpacing = 0.14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textMuted)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun marginParams(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setStatus(message: String, active: Boolean) {
        status.text = "  $message"
        statusDot.setTextColor(if (active) mint else Color.rgb(150, 165, 190))
    }

    private fun checkPermissionsAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            return
        }
        startTranslator()
    }

    private fun startTranslator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createScreenCaptureIntent(), captureRequestCode)
        } else {
            launchTranslationService(null, 0)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != captureRequestCode) return
        if (resultCode == Activity.RESULT_OK && data != null) {
            launchTranslationService(data, resultCode)
        } else {
            setStatus("Захват аудио не разрешён — запускаю микрофон", false)
            launchTranslationService(null, 0)
        }
    }

    private fun launchTranslationService(projectionData: Intent?, resultCode: Int) {
        val intent = Intent(this, TranslationService::class.java)
        if (projectionData != null) {
            intent.putExtra(TranslationService.EXTRA_RESULT_CODE, resultCode)
            intent.putExtra(TranslationService.EXTRA_RESULT_DATA, projectionData)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        setStatus(if (projectionData != null) "Захват звука запущен" else "Перевод включён через микрофон", true)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (requestCode == 100 || requestCode == 101) checkPermissionsAndStart()
        } else setStatus("Разрешения не выданы", false)
    }
}