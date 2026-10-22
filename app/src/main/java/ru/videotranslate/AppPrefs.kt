package ru.videotranslate

import android.content.Context

object AppPrefs {
    const val FILE = "video_translate_settings"
    const val THEME = "theme"
    const val ENGINE = "translation_engine"
    const val RECOGNITION_ENGINE = "recognition_engine"
    const val TTS_VOLUME = "tts_volume"
    const val AI_BASE_URL = "ai_base_url"
    const val AI_API_KEY = "ai_api_key"
    const val AI_MODEL = "ai_model"
    const val CHUNK_SECONDS = "chunk_seconds"
    const val TTS_RATE = "tts_rate"
    const val FLOATING_ENABLED = "floating_enabled"
    const val IS_RUNNING = "translation_running"

    fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun theme(context: Context): String = prefs(context).getString(THEME, "system") ?: "system"

    fun palette(context: Context): Palette {
        val dark = when (theme(context)) {
            "light" -> false
            "dark", "midnight", "ocean", "forest" -> true
            else -> (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val name = theme(context)
        val bg = when (name) {
            "light" -> 0xFFF5F7FB.toInt()
            "ocean" -> 0xFF071A2B.toInt()
            "forest" -> 0xFF10231E.toInt()
            "midnight" -> 0xFF090B12.toInt()
            else -> if (dark) 0xFF0D111B.toInt() else 0xFFF5F7FB.toInt()
        }
        val surface = when (name) {
            "light" -> 0xFFFFFFFF.toInt()
            "ocean" -> 0xFF102A40.toInt()
            "forest" -> 0xFF1A342C.toInt()
            "midnight" -> 0xFF171B26.toInt()
            else -> if (dark) 0xFF191F2D.toInt() else 0xFFFFFFFF.toInt()
        }
        val accent = when (name) {
            "ocean" -> 0xFF55B8FF.toInt()
            "forest" -> 0xFF65D6A6.toInt()
            "midnight" -> 0xFF9B8CFF.toInt()
            else -> 0xFF6C63FF.toInt()
        }
        return Palette(bg, surface, if (dark) 0xFFF3F5FC.toInt() else 0xFF182033.toInt(),
            if (dark) 0xFF9CA9C0.toInt() else 0xFF69758A.toInt(), accent, dark)
    }

    data class Palette(val background: Int, val surface: Int, val text: Int, val muted: Int,
                       val accent: Int, val dark: Boolean)
}
