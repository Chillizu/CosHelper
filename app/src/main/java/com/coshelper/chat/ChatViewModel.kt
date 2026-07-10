package com.coshelper.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = NearbyChatManager(application)

    val state: StateFlow<ChatState> = manager.state
    val events = manager.events

    private val _statusText = MutableStateFlow("待机")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    init {
        viewModelScope.launch {
            state.collect { s ->
                _statusText.value = when (s) {
                    is ChatState.Idle -> "待机"
                    is ChatState.Searching -> "搜索中..."
                    is ChatState.Connecting -> "连接中..."
                    is ChatState.Connected -> "已连接"
                    is ChatState.Sending -> "正在说话..."
                }
            }
        }
    }

    fun startChat() = manager.start()
    fun stopChat() = manager.stop()
    fun pressPtt() = manager.startPtt()
    fun releasePtt() = manager.stopPtt()

    override fun onCleared() {
        super.onCleared()
        manager.cleanup()
    }
}
