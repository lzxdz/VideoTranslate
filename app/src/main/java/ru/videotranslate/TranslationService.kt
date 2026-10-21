package ru.videotranslate

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class TranslationService : Service(), TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    @Volatile private var ttsReady = false
    @Volatile private var lastText = ""
    @Volatile private var lastAt = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Готовлю перевод…"))
        tts = TextToSpeech(this, this)
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(listener)
            startListening()
        } else updateNotification("Распознавание речи недоступно на этом устройстве")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try { recognizer?.startListening(intent) } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            updateNotification("Не удалось запустить распознавание")
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) { updateNotification("Слушаю английскую речь…") }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            android.os.Handler(mainLooper).postDelayed({ if (recognizer != null) startListening() }, 450)
        }
        override fun onResults(results: android.os.Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) processRecognized(text)
            android.os.Handler(mainLooper).postDelayed({ if (recognizer != null) startListening() }, 250)
        }
        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank() && text.length >= 28) processRecognized(text)
        }
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
    }

    private fun processRecognized(text: String) {
        val now = System.currentTimeMillis()
        if (text.equals(lastText, true) && now - lastAt < 8_000) return
        if (text.length < 8 || !busy.compareAndSet(false, true)) return
        lastText = text
        lastAt = now
        updateNotification("Перевожу: ${text.take(45)}")
        executor.execute {
            try {
                val prefs = getSharedPreferences("config", MODE_PRIVATE)
                val endpoint = prefs.getString("endpoint", "").orEmpty()
                val key = prefs.getString("key", "").orEmpty()
                val model = prefs.getString("model", "gpt-4o-mini").orEmpty()
                val translated = TranslationApi.translate(endpoint, key, model, text)
                android.os.Handler(mainLooper).post {
                    if (ttsReady && translated.isNotBlank()) {
                        tts?.speak(translated, TextToSpeech.QUEUE_ADD, null, "vt-${now}")
                        updateNotification("RU: ${translated.take(65)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Translation failed", e)
                updateNotification("Ошибка перевода: проверь API и интернет")
            } finally { busy.set(false) }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "VideoTranslate", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, StopServiceReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("VideoTranslate")
            .setContentText(text)
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
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "VideoTranslate"
        private const val CHANNEL_ID = "video_translate"
        private const val NOTIFICATION_ID = 42
    }
}
