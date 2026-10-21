package ru.videotranslate

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.atomic.AtomicBoolean

/** Reuses the ML Kit client and downloads the model once, reducing per-phrase delay. */
object TranslationApi {
    @Volatile private var translator: Translator? = null
    private val modelReady = AtomicBoolean(false)
    private val modelLoading = AtomicBoolean(false)
    private val waiting = mutableListOf<Pair<String, (Result<String>) -> Unit>>()

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
        if (modelReady.get()) {
            onReady?.invoke(true)
            return
        }
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
                queued.forEach { (text, callback) -> translateReady(client, text, callback) }
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
        val client = getTranslator(context.applicationContext)
        if (!modelReady.get()) {
            synchronized(waiting) { waiting.add(source to callback) }
            warmUp(context.applicationContext)
            return
        }
        translateReady(client, source, callback)
    }

    private fun translateReady(client: Translator, source: String, callback: (Result<String>) -> Unit) {
        client.translate(source)
            .addOnSuccessListener { callback(Result.success(it)) }
            .addOnFailureListener { callback(Result.failure(it)) }
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