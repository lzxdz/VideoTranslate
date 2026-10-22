package ru.videotranslate

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(CrashDebugApplication.PREFS, MODE_PRIVATE)
        val report = prefs.getString(CrashDebugApplication.KEY_REPORT, "Отчёт об ошибке не найден.") ?: "Отчёт об ошибке не найден."
        val time = prefs.getLong(CrashDebugApplication.KEY_TIME, 0L)
        val timestamp = if (time > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time)) else "неизвестно"
        val fullReport = "VideoTranslate crash report\nВремя: $timestamp\n\n$report"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        root.addView(TextView(this).apply { text = "Отчёт об ошибке"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD })
        root.addView(TextView(this).apply {
            text = "Отчёт хранится только на этом телефоне. Он не отправляется автоматически. Скопируй текст и пришли его в чат."
            textSize = 14f; setPadding(0, 12, 0, 16)
        })
        root.addView(Button(this).apply {
            text = "Скопировать отчёт"; isAllCaps = false
            setOnClickListener {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("VideoTranslate crash report", fullReport))
                Toast.makeText(this@CrashReportActivity, "Отчёт скопирован", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text = "Отправить через…"; isAllCaps = false
            setOnClickListener {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "VideoTranslate — ошибка настроек")
                    putExtra(Intent.EXTRA_TEXT, fullReport)
                }, "Отправить отчёт об ошибке"))
            }
        })
        root.addView(TextView(this).apply {
            text = fullReport; textSize = 12f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(0, 16, 0, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
