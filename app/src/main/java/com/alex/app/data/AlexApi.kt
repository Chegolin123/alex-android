package com.alex.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Результат обращения к Алексу. */
sealed interface AlexResult {
    data class Text(val answer: String, val fast: Boolean, val seconds: Double) : AlexResult
    data class Voice(val youSaid: String, val reply: String, val wav: ByteArray) : AlexResult
    data class Failed(val message: String) : AlexResult
}

/**
 * Клиент сервера Алекса (STT/TTS + мозг на домашней машине).
 * POST /chat  {text} → {text, fast, seconds}
 * POST /voice (WAV) → WAV + заголовки X-You-Said / X-Reply (hex UTF-8)
 * POST /tts   {text} → WAV
 */
class AlexApi(
    private val baseUrl: String,
    private val token: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun request(path: String): Request.Builder =
        Request.Builder()
            .url("$baseUrl$path")
            .apply { if (token.isNotBlank()) header("X-Token", token) }

    suspend fun chat(text: String): AlexResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("text", text).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        try {
            client.newCall(request("/chat").post(body).build()).execute().use { resp ->
                val payload = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext AlexResult.Failed("сервер ответил ${resp.code}: $payload")
                }
                val json = JSONObject(payload)
                AlexResult.Text(
                    answer = json.optString("text").ifBlank { "…" },
                    fast = json.optBoolean("fast", false),
                    seconds = json.optDouble("seconds", 0.0),
                )
            }
        } catch (e: IOException) {
            AlexResult.Failed("нет связи с Алексом: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun voice(wav: ByteArray): AlexResult = withContext(Dispatchers.IO) {
        try {
            client.newCall(
                request("/voice")
                    .post(wav.toRequestBody("application/octet-stream".toMediaType()))
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val payload = resp.body?.string().orEmpty()
                    return@withContext AlexResult.Failed("сервер ответил ${resp.code}: $payload")
                }
                val youSaid = resp.header("X-You-Said").orEmpty().let { hexToUtf8(it) }
                val reply = resp.header("X-Reply").orEmpty().let { hexToUtf8(it) }
                val wavBytes = resp.body?.bytes() ?: error("пустой ответ")
                AlexResult.Voice(youSaid.ifBlank { "…" }, reply.ifBlank { "…" }, wavBytes)
            }
        } catch (e: IOException) {
            AlexResult.Failed("нет связи с Алексом: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun tts(text: String): ByteArray? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("text", text).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        try {
            client.newCall(request("/tts?engine=f5").post(body).build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun hexToUtf8(hex: String): String {
        if (hex.isBlank()) return ""
        return runCatching {
            val bytes = ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            String(bytes, Charsets.UTF_8)
        }.getOrDefault("")
    }
}
