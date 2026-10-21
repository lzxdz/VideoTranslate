package ru.videotranslate

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object TranslationApi {
    fun translate(endpoint: String, apiKey: String, model: String, source: String): String {
        require(endpoint.startsWith("https://")) { "Endpoint должен использовать HTTPS" }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            val body = JSONObject().apply {
                put("model", model)
                put("temperature", 0.2)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Ты профессиональный переводчик английской разговорной речи на естественный русский. Переводи только предоставленный фрагмент. Сохраняй смысл, имена и тон. Не добавляй объяснений, кавычек или вводных слов.")
                }).put(JSONObject().apply {
                    put("role", "user")
                    put("content", source)
                }))
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("API HTTP $code: ${response.take(300)}")
            return JSONObject(response).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
        } finally { connection.disconnect() }
    }
}
