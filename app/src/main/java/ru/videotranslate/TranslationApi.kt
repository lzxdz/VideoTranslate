package ru.videotranslate

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Offline-capable English -> Russian translation.
 * The first use downloads Google's language model; no API key or paid endpoint is needed.
 */
object TranslationApi {
    fun translate(context: Context, source: String): String {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.RUSSIAN)
            .build()
        val translator = Translation.getClient(options)
        val latch = CountDownLatch(1)
        val translated = AtomicReference<String?>()
        val failure = AtomicReference<Throwable?>()

        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                translator.translate(source)
                    .addOnSuccessListener {
                        translated.set(it)
                        latch.countDown()
                    }
                    .addOnFailureListener {
                        failure.set(it)
                        latch.countDown()
                    }
            }
            .addOnFailureListener {
                failure.set(it)
                latch.countDown()
            }

        try {
            if (!latch.await(180, TimeUnit.SECONDS)) {
                throw IllegalStateException("Загрузка модели или перевод заняли слишком много времени. Проверь интернет.")
            }
            failure.get()?.let {
                throw IllegalStateException("Не удалось загрузить модель перевода. Проверь интернет и повтори попытку.", it)
            }
            return translated.get().orEmpty()
        } finally {
            translator.close()
        }
    }
}
