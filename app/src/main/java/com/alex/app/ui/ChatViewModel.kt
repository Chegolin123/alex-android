package com.alex.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.app.data.AlexApi
import com.alex.app.data.ChatMessage
import com.alex.app.data.ChatStore
import com.alex.app.data.VoiceRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Состояние экрана чата. */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    val recording: Boolean = false,
    val status: String = "",
    val showSettings: Boolean = false,
)

class ChatViewModel(private val store: ChatStore) : ViewModel() {

    private val _state = MutableStateFlow(ChatState(messages = store.loadMessages()))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val recorder = VoiceRecorder()
    private var api = AlexApi(store.serverUrl, store.token)

    fun refreshApi() {
        api = AlexApi(store.serverUrl, store.token)
    }

    fun toggleSettings() {
        _state.value = _state.value.copy(showSettings = !_state.value.showSettings)
    }

    fun sendText(text: String) {
        if (text.isBlank() || _state.value.busy) return
        push(ChatMessage(text = text, fromUser = true))
        _state.value = _state.value.copy(busy = true, status = "Алекс думает…")
        viewModelScope.launch {
            when (val result = api.chat(text)) {
                is com.alex.app.data.AlexResult.Text -> {
                    push(ChatMessage(text = result.answer, fromUser = false))
                    _state.value = _state.value.copy(busy = false, status = "")
                }
                is com.alex.app.data.AlexResult.Failed -> {
                    _state.value = _state.value.copy(busy = false, status = result.message)
                }
                else -> _state.value = _state.value.copy(busy = false, status = "")
            }
        }
    }

    fun startVoice() {
        if (_state.value.busy) return
        _state.value = _state.value.copy(recording = true, status = "Слушаю…")
        recorder.start()
    }

    fun stopVoice() {
        if (!_state.value.recording) return
        val wav = recorder.stop()
        _state.value = _state.value.copy(recording = false, busy = true, status = "Алекс думает…")
        viewModelScope.launch {
            when (val result = api.voice(wav)) {
                is com.alex.app.data.AlexResult.Voice -> {
                    push(ChatMessage(text = result.youSaid, fromUser = true, isVoice = true))
                    push(ChatMessage(text = result.reply, fromUser = false, isVoice = true))
                    _state.value = _state.value.copy(busy = false, status = "")
                }
                is com.alex.app.data.AlexResult.Failed -> {
                    _state.value = _state.value.copy(busy = false, status = result.message)
                }
                else -> _state.value = _state.value.copy(busy = false, status = "")
            }
        }
    }

    fun cancelVoice() {
        if (_state.value.recording) {
            recorder.cancel()
            _state.value = _state.value.copy(recording = false, status = "")
        }
    }

    private fun push(message: ChatMessage) {
        val updated = _state.value.messages + message
        _state.value = _state.value.copy(messages = updated)
        store.saveMessages(updated)
    }
}
