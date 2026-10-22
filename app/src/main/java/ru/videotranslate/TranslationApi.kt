package ru.videotranslate

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Google ML Kit offline translation plus optional OpenAI-compatible AI translation. */
object TranslationApi {
    @Volatile private var translator: Translator? = null
    private val modelReady = AtomicBoolean(false)
    private val modelLoading = AtomicBoolean(false)
    private val waiting = mutableListOf<Pair<String, (Result<String>) -> Unit>>()
    private val aiExecutor = Executors.newSingleThreadExecutor()

    @Synchronized
    private fun getTranslator(context: Context): Translator {
        translator?.let { return it }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.RUSSIAN)
            .build()
        return Translation.getClient(options).also { translator = it }
    }

    fun warmUp(context: Context, onReady: ((Boolean) -> Unit)? = null) {
        val client = getTranslator(context.applicationContext)
        if (modelReady.get()) { onReady?.invoke(true); return }
        synchronized(this) {
            if (modelLoading.get()) return
            modelLoading.set(true)
        }
        client.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                modelReady.set(true)
                modelLoading.set(false)
                onReady?.invoke(true)
                val queued: List<Pair<String, (Result<String>) -> Unit>>
                synchronized(waiting) { queued = waiting.toList(); waiting.clear() }
                queued.forEach { (text, callback) -> translateGoogleReady(client, text, callback) }
            }
            .addOnFailureListener { error ->
                modelLoading.set(false)
                onReady?.invoke(false)
                val queued: List<Pair<String, (Result<String>) -> Unit>>
                synchronized(waiting) { queued = waiting.toList(); waiting.clear() }
                queued.forEach { (_, callback) -> callback(Result.failure(error)) }
            }
    }

    fun translateAsync(context: Context, source: String, callback: (Result<String>) -> Unit) {
        val app = context.applicationContext
        if (AppPrefs.prefs(app).getString(AppPrefs.ENGINE, "google") == "ai") {
            translateWithAi(app, source, callback)
            return
        }
        val client = getTranslator(app)
        if (!modelReady.get()) {
            synchronized(waiting) { waiting.add(source to callback) }
            warmUp(app)
            return
        }
        translateGoogleReady(client, source, callback)
    }

    private fun translateGoogleReady(client: Translator, source: String, callback: (Result<String>) -> Unit) {
        client.translate(source)
            .addOnSuccessListener { callback(Result.success(it)) }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    private fun translateWithAi(context: Context, source: String, callback: (Result<String>) -> Unit) {
        val prefs = AppPrefs.prefs(context)
        val base = (prefs.getString(AppPrefs.AI_BASE_URL, "https://api.openai.com/v1") ?: "").trimEnd('/')
        val key = prefs.getString(AppPrefs.AI_API_KEY, "")?.trim().orEmpty()
        val model = prefs.getString(AppPrefs.AI_MODEL, "gpt-4o-mini")?.trim().orEmpty()
        if (!base.startsWith("https://") || key.isBlank() || model.isBlank()) {
            callback(Result.failure(IllegalStateException("Настрой API-адрес, модель и API-ключ ИИ в настройках")))
            return
        }
        aiExecutor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("$base/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val body = JSONObject()
                    .put("model", model)
                    .put("temperature", 0.1)
                    .put("max_tokens", 256)
                    .put("messages", org.json.JSONArray()
                        .put(JSONObject().put("role", "system").put("content",
                            "Translate the following spoken English into natural concise Russian. Return only the Russian translation. Preserve meaning and do not add explanations."))
                        .put(JSONObject().put("role", "user").put("content", source)))
                    .toString()
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                if (code !in 200..299) error("AI API вернул HTTP $code")
                val text = JSONObject(response).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim()
                if (text.isBlank()) error("ИИ вернул пустой перевод")
                callback(Result.success(text))
            } catch (e: Exception) {
                callback(Result.failure(e))
            } finally {
                connection?.disconnect()
            }
        }
    }

    @Synchronized
    fun close() {
        translator?.close()
        translator = null
        modelReady.set(false)
        modelLoading.set(false)
        synchronized(waiting) { waiting.clear() }
    }
}
