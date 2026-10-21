package ru.videotranslate

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class TranslationService : Service(), TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val translating = AtomicBoolean(false)
    @Volatile private var ttsReady = false
    @Volatile private var lastText = ""
    @Volatile private var lastAt = 0L
    @Volatile private var pendingText: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Подготовка перевода…"))
        tts = TextToSpeech(this, this)
        TranslationApi.warmUp(applicationContext) { ready ->
            android.os.Handler(mainLooper).post {
                if (recognizer != null) updateNotification(if (ready) "Модель перевода готова" else "Не удалось загрузить модель")
            }
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(listener)
            startListening()
        } else updateNotification("Распознавание недоступно")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            // Partial results are unstable mid-sentence, so translate only final transcripts.
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 650L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 350L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 700L)
        }
        try { recognizer?.startListening(intent) } catch (_: Exception) {
            updateNotification("Перезапускаю распознавание")
            scheduleListening(900)
        }
    }

    private fun scheduleListening(delay: Long) {
        android.os.Handler(mainLooper).postDelayed({ if (recognizer != null) startListening() }, delay)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) { updateNotification("Слушаю английскую речь") }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) { scheduleListening(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1000 else 450) }
        override fun onResults(results: android.os.Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) processRecognized(text.trim())
            scheduleListening(220)
        }
        override fun onPartialResults(partialResults: android.os.Bundle?) {}
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
    }

    private fun processRecognized(text: String) {
        val now = System.currentTimeMillis()
        if (text.length < 5) return
        if (text.equals(lastText, true) && now - lastAt < 12_000) return
        lastText = text
        lastAt = now
        if (!translating.compareAndSet(false, true)) {
            // Bound backlog to one newest phrase; do not let speech queue grow.
            pendingText = text
            return
        }
        translate(text)
    }

    private fun translate(text: String) {
        updateNotification("Перевожу речь…")
        TranslationApi.translateAsync(applicationContext, text) { result ->
            android.os.Handler(mainLooper).post {
                result.onSuccess { translated ->
                    if (ttsReady && translated.isNotBlank()) {
                        // Avoid speaking old sentences long after the video has moved on.
                        tts?.speak(translated, TextToSpeech.QUEUE_FLUSH, null, "vt-" + System.currentTimeMillis())
                    }
                    updateNotification("Слушаю английскую речь")
                }.onFailure {
                    updateNotification("Перевод недоступен — проверь интернет")
                }
                translating.set(false)
                val next = pendingText
                pendingText = null
                if (!next.isNullOrBlank() && next != text && translating.compareAndSet(false, true)) translate(next)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsReady) updateNotification("Установи русский голос в настройках Android")
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "VideoTranslate", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Статус фонового перевода"
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, TranslationService::class.java).setAction(ACTION_STOP)
        val stop = PendingIntent.getService(this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("VideoTranslate")
            .setContentText(text)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Остановить", stop).build())
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        TranslationApi.close()
        lastText = ""
        lastAt = 0L
        pendingText = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "video_translate"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "ru.videotranslate.action.STOP"
    }
}