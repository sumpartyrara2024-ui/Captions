package com.example.captions

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TranslateResult(val english: String, val heard: String, val error: String)

object Translator {
    fun send(workerUrl: String, b64Wav: String, lang: String): TranslateResult {
        return try {
            val body = JSONObject().put("audio", b64Wav).put("lang", lang).toString()
            val conn = URL(workerUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code in 200..299) {
                val json = JSONObject(resp)
                TranslateResult(json.optString("text", "").trim(), json.optString("original", "").trim(), "")
            } else {
                TranslateResult("", "", "server $code: " + resp.take(80))
            }
        } catch (e: Exception) {
            TranslateResult("", "", "network error: " + e.message)
        }
    }
}
