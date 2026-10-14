package com.alex.app.data.update

import com.alex.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(
        val versionName: String,
        val apkUrl: String,
        val notes: String,
    ) : UpdateCheckResult

    data class Error(val message: String) : UpdateCheckResult
}

/** Проверка релиза в GitHub: сравниваем versionName семантически. */
class UpdateChecker {

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(UpdateConfig.LATEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 15_000
                readTimeout = 20_000
            }
            val code = conn.responseCode
            val payload = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            if (code !in 200..299) {
                return@withContext UpdateCheckResult.Error("GitHub вернул $code")
            }
            val json = JSONObject(payload)
            val tag = json.optString("tag_name").removePrefix("v")
            val notes = json.optString("body").ifBlank { "Без описания изменений." }
            val asset = json.optJSONArray("assets")?.let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }
                    .firstOrNull { it.optString("name") == UpdateConfig.APK_ASSET_NAME }
            }
            val apkUrl = asset?.optString("browser_download_url")
                ?: return@withContext UpdateCheckResult.Error("APK не найден в релизе $tag")
            if (isNewer(tag, BuildConfig.VERSION_NAME)) {
                UpdateCheckResult.Available(tag, apkUrl, notes)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error("Не удалось проверить: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn?.disconnect()
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val r = parseSemver(remote)
        val c = parseSemver(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun parseSemver(v: String): List<Int> {
        val parts = v.split(".")
        return List(3) { i -> parts.getOrNull(i)?.takeWhile { ch -> ch.isDigit() }?.toIntOrNull() ?: 0 }
    }

    /** Скачивание APK в кэш приложения. */
    suspend fun downloadApk(apkUrl: String, target: java.io.File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15_000
                        readTimeout = 120_000
                        instanceFollowRedirects = true
                    }
                    conn.inputStream.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.length() > 0
                } finally {
                    conn?.disconnect()
                }
            } catch (e: Exception) {
                false
            }
        }
}
