package ru.videotranslate

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.app.Notification
import java.io.PrintWriter
import java.io.StringWriter

class CrashDebugApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val report = StringWriter().also { writer ->
                error.printStackTrace(PrintWriter(writer))
            }.toString()
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_REPORT, "Thread: ${thread.name}\n\n$report")
                .putLong(KEY_TIME, System.currentTimeMillis())
                .commit()
            Log.e("VideoTranslateCrash", report)
            try { postCrashNotification() } catch (notificationError: Throwable) {
                Log.e("VideoTranslateCrash", "Could not show crash notification", notificationError)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun postCrashNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Отчёты об ошибках", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            this, 9001, Intent(this, CrashReportActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("VideoTranslate: произошёл сбой")
            .setContentText("Нажми, чтобы открыть и скопировать отчёт об ошибке")
            .setStyle(Notification.BigTextStyle().bigText("Отчёт сохранён на устройстве. Открой его и отправь разработчику, чтобы исправить настройки."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val PREFS = "crash_debug_report"
        const val KEY_REPORT = "last_report"
        const val KEY_TIME = "last_report_time"
        const val CHANNEL_ID = "videotranslate_crash_reports"
        const val NOTIFICATION_ID = 9010
    }
}
