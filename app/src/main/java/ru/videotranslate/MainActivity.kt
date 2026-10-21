package ru.videotranslate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import android.graphics.Color

class MainActivity : Activity() {
    private lateinit var endpoint: EditText
    private lateinit var apiKey: EditText
    private lateinit var model: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(12, 20, 32))
        }
        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 10, 0, 5)
        }
        fun edit(hint: String, value: String, secret: Boolean = false): EditText {
            return EditText(this).apply {
                this.hint = hint
                setText(value)
                textSize = 14f
                setTextColor(Color.WHITE)
                setHintTextColor(Color.LTGRAY)
                setSingleLine(true)
                if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPadding(12, 8, 12, 8)
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(60, 150, 255))
            }
        }
        root.addView(TextView(this).apply {
            text = "VideoTranslate"
            textSize = 28f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Английская речь → русский перевод → озвучка"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 4, 0, 14)
        })
        root.addView(label("Совместимый API endpoint (Chat Completions)"))
        endpoint = edit("https://your-server.example/v1/chat/completions",
            prefs.getString("endpoint", "") ?: "")
        root.addView(endpoint)
        root.addView(label("API key (хранится локально на устройстве)"))
        apiKey = edit("API key", prefs.getString("key", "") ?: "", true)
        root.addView(apiKey)
        root.addView(label("Название модели перевода"))
        model = edit("например, gpt-4o-mini", prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini")
        root.addView(model)

        val note = TextView(this).apply {
            text = "Прототип распознаёт речь через системный Android SpeechRecognizer, слушая звук через микрофон. Поэтому звук YouTube должен быть слышен динамиком; наушники могут не подойти. Перевод выполняется через указанный API, затем читается системным TTS."
            textSize = 13f
            setTextColor(Color.rgb(190, 205, 220))
            setPadding(0, 12, 0, 12)
        }
        root.addView(note)
        status = TextView(this).apply {
            text = "Статус: остановлено"
            textSize = 14f
            setTextColor(Color.rgb(80, 220, 170))
            setPadding(0, 8, 0, 8)
        }
        root.addView(status)

        val start = Button(this).apply {
            text = "Сохранить и запустить перевод"
            setOnClickListener {
                prefs.edit().putString("endpoint", endpoint.text.toString().trim())
                    .putString("key", apiKey.text.toString().trim())
                    .putString("model", model.text.toString().trim()).apply()
                if (endpoint.text.isBlank() || apiKey.text.isBlank()) {
                    status.text = "Укажи endpoint и API key (или подними свой совместимый сервер)."
                    return@setOnClickListener
                }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
                    return@setOnClickListener
                }
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                    return@setOnClickListener
                }
                startTranslator()
            }
        }
        root.addView(start)
        root.addView(Button(this).apply {
            text = "Остановить перевод"
            setOnClickListener {
                stopService(Intent(this@MainActivity, TranslationService::class.java))
                status.text = "Статус: остановлено"
            }
        })
        root.addView(Button(this).apply {
            text = "Разрешить плавающую кнопку (необязательно)"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                } else status.text = "Разрешение плавающего окна уже выдано."
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun startTranslator() {
        val intent = Intent(this, TranslationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        status.text = "Статус: сервис запущен. Переключись в YouTube и включи звук."
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (requestCode == 100 || requestCode == 101) startTranslator()
        } else status.text = "Нужны разрешения микрофона и, на Android 13+, уведомлений."
    }
}
