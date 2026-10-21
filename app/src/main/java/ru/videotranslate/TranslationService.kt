package ru.videotranslate

import android.app.*
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline speech recognition using whisper.cpp. The model is downloaded once on first run;
 * after that ASR and ML Kit translation run locally. Audio is captured from the microphone,
 * not directly from another app's playback stream.
 */
class TranslationService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    @Volatile private var whisperModel: WhisperModel? = null
    private val stopped = AtomicBoolean(false)
    private val recording = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val chunks = ArrayBlockingQueue<File>(2)
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var lastText = ""
    @Volatile private var lastAt = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Подготовка офлайн-модели речи…"))
        tts = TextToSpeech(this, this)
        TranslationApi.warmUp(applicationContext) { ready ->
            if (!ready) updateNotification("Не удалось загрузить модель перевода — нужен интернет")
        }
        executor.execute {
            try {
                val modelFile = File(filesDir, "models/ggml-base.en.bin")
                if (!modelFile.exists() || modelFile.length() < 100_000_000L) {
                    modelFile.parentFile?.mkdirs()
                    downloadModel(modelFile)
                }
                updateNotification("Загружаю модель распознавания…")
                whisperModel = runBlocking { Whisper.loadModel(this@TranslationService, modelFile.absolutePath) }
                if (!stopped.get()) {
                    updateNotification("Офлайн-распознавание готово")
                    startAudioPipeline()
                }
            } catch (e: Exception) {
                updateNotification("Не удалось подготовить модель: проверь интернет и память")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopped.set(true)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun downloadModel(destination: File) {
        val url = URL(MODEL_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "VideoTranslate-Android")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) error("Model download HTTP ${connection.responseCode}")
            val temp = File(destination.parentFile, destination.name + ".part")
            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(temp)).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (!stopped.get()) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (stopped.get()) {
                temp.delete()
                return
            }
            if (!temp.renameTo(destination)) {
                temp.copyTo(destination, overwrite = true)
                temp.delete()
            }
            if (destination.length() < 100_000_000L) {
                destination.delete()
                error("Downloaded Whisper model is incomplete")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun startAudioPipeline() {
        if (!recording.compareAndSet(false, true)) return
        Thread({ processChunks() }, "whisper-processor").start()
        Thread({ recordChunks() }, "whisper-recorder").start()
    }

    private fun recordChunks() {
        val sampleRate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            updateNotification("Микрофон недоступен")
            recording.set(false)
            return
        }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, sampleRate * 2)
        )
        audioRecord = recorder
        try {
            recorder.startRecording()
            updateNotification("Слушаю английскую речь офлайн")
            val readBuffer = ByteArray(4096)
            val chunkBytes = sampleRate * 2 * CHUNK_SECONDS
            var pcm = java.io.ByteArrayOutputStream(chunkBytes)
            while (!stopped.get() && recording.get()) {
                val count = recorder.read(readBuffer, 0, readBuffer.size)
                if (count > 0) pcm.write(readBuffer, 0, count)
                if (pcm.size() >= chunkBytes) {
                    val file = File(cacheDir, "whisper-${System.currentTimeMillis()}.wav")
                    writeWav(file, pcm.toByteArray(), sampleRate)
                    pcm = java.io.ByteArrayOutputStream(chunkBytes)
                    if (!chunks.offer(file)) {
                        chunks.poll()?.delete()
                        chunks.offer(file)
                    }
                }
            }
        } catch (_: Exception) {
            if (!stopped.get()) updateNotification("Ошибка записи звука")
        } finally {
            try { recorder.stop() } catch (_: Exception) {}
            recorder.release()
            audioRecord = null
            recording.set(false)
        }
    }

    private fun processChunks() {
        while (!stopped.get()) {
            val file = try { chunks.take() } catch (_: InterruptedException) { break }
            try {
                val model = whisperModel ?: continue
                val result = runBlocking {
                    Whisper.transcribe(model, file.absolutePath, WhisperConfig(language = "en"))
                }
                val text = result.text.trim()
                if (text.length >= 5) {
                    val now = System.currentTimeMillis()
                    if (!text.equals(lastText, true) || now - lastAt > 12_000) {
                        lastText = text
                        lastAt = now
                        translate(text)
                    }
                }
            } catch (_: Exception) {
                if (!stopped.get()) updateNotification("Распознавание не удалось — продолжаю слушать")
            } finally {
                file.delete()
            }
        }
    }

    private fun translate(text: String) {
        TranslationApi.translateAsync(applicationContext, text) { result ->
            result.onSuccess { translated ->
                if (ttsReady && translated.isNotBlank() && !stopped.get()) {
                    android.os.Handler(mainLooper).post {
                        tts?.speak(translated, TextToSpeech.QUEUE_FLUSH, null, "vt-" + System.currentTimeMillis())
                    }
                }
            }.onFailure {
                updateNotification("Перевод недоступен — проверь загрузку модели")
            }
        }
    }

    private fun writeWav(file: File, pcm: ByteArray, sampleRate: Int) {
        java.io.DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            val dataSize = pcm.size
            out.writeBytes("RIFF")
            writeIntLE(out, 36 + dataSize)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            writeIntLE(out, 16)
            writeShortLE(out, 1)
            writeShortLE(out, 1)
            writeIntLE(out, sampleRate)
            writeIntLE(out, sampleRate * 2)
            writeShortLE(out, 2)
            writeShortLE(out, 16)
            out.writeBytes("data")
            writeIntLE(out, dataSize)
            out.write(pcm)
        }
    }

    private fun writeIntLE(out: java.io.DataOutputStream, value: Int) {
        out.writeByte(value and 0xff); out.writeByte(value shr 8 and 0xff)
        out.writeByte(value shr 16 and 0xff); out.writeByte(value shr 24 and 0xff)
    }

    private fun writeShortLE(out: java.io.DataOutputStream, value: Int) {
        out.writeByte(value and 0xff); out.writeByte(value shr 8 and 0xff)
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
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, TranslationService::class.java).setAction(ACTION_STOP)
        val stop = PendingIntent.getService(this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("VideoTranslate · Offline")
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
        stopped.set(true)
        recording.set(false)
        try { audioRecord?.stop() } catch (_: Exception) {}
        executor.shutdownNow()
        chunks.forEach { it.delete() }
        whisperModel?.let { model -> try { Whisper.releaseModel(model) } catch (_: Exception) {} }
        whisperModel = null
        tts?.stop()
        tts?.shutdown()
        TranslationApi.close()
        lastText = ""
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "video_translate"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "ru.videotranslate.action.STOP"
        private const val CHUNK_SECONDS = 6
        private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin?download=true"
    }
}
