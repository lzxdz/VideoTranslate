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
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(12, 20, 32))
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
        root.addView(TextView(this).apply {
            text = "Перевод выполняется на устройстве с помощью бесплатной модели Google ML Kit. API-ключ не нужен. При первом запуске приложение скачает языковую модель через интернет. Распознавание слушает микрофон, поэтому звук видео должен быть слышен динамиком. Для озвучки нужен установленный русский голос Android."
            textSize = 14f
            setTextColor(Color.rgb(210, 220, 232))
            setPadding(0, 12, 0, 18)
        })
        status = TextView(this).apply {
            text = "Статус: остановлено"
            textSize = 14f
            setTextColor(Color.rgb(80, 220, 170))
            setPadding(0, 8, 0, 8)
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Запустить перевод"
            setOnClickListener { checkPermissionsAndStart() }
        })
        root.addView(Button(this).apply {
            text = "Остановить перевод"
            setOnClickListener {
                stopService(Intent(this@MainActivity, TranslationService::class.java))
                status.text = "Статус: остановлено"
            }
        })
        root.addView(Button(this).apply {
            text = "Настройки плавающего окна (необязательно)"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                } else status.text = "Разрешение плавающего окна уже выдано."
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
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
        val intent = Intent(this, TranslationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        status.text = "Сервис запущен. Переключись в YouTube и включи звук."
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (requestCode == 100 || requestCode == 101) checkPermissionsAndStart()
        } else status.text = "Для работы нужны разрешения микрофона и уведомлений."
    }
}
