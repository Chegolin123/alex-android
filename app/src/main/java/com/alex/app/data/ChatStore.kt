package com.alex.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** История чата и настройки подключения (SharedPreferences, JSON). */
class ChatStore(context: Context) {

    private val prefs = context.getSharedPreferences("alex", Context.MODE_PRIVATE)

    fun loadMessages(): List<ChatMessage> {
        val raw = prefs.getString("messages", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    text = o.getString("text"),
                    fromUser = o.getBoolean("fromUser"),
                    timestamp = o.optLong("ts", System.currentTimeMillis()),
                    isVoice = o.optBoolean("voice", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveMessages(messages: List<ChatMessage>) {
        val arr = JSONArray()
        messages.takeLast(200).forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("text", m.text)
                    put("fromUser", m.fromUser)
                    put("ts", m.timestamp)
                    put("voice", m.isVoice)
                }
            )
        }
        prefs.edit().putString("messages", arr.toString()).apply()
    }

    var serverUrl: String
        get() = prefs.getString("server_url", "http://100.75.210.79:8090")
            ?: "http://100.75.210.79:8090"
        set(value) = prefs.edit().putString("server_url", value.trimEnd('/')).apply()

    var token: String
        get() = prefs.getString("token", "a310e11642d209049f777a5df68cb775") ?: ""
        set(value) = prefs.edit().putString("token", value).apply()

    var lastVersion: String
        get() = prefs.getString("last_version", "") ?: ""
        set(value) = prefs.edit().putString("last_version", value).apply()
}
