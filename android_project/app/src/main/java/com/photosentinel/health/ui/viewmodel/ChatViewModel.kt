package com.photosentinel.health.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.presentation.repository.HealthAssistantDataSource
import com.photosentinel.health.presentation.repository.HealthAssistantRepository
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: HealthAssistantDataSource = HealthAssistantRepository()
) : ViewModel() {
    private val sessionId = "session_${System.currentTimeMillis()}"

    val messages = mutableStateListOf<ChatMessage>()

    var isLoading by mutableStateOf(false)
        private set

    var isServerOnline by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        checkServer()
        appendMessage(
            ChatMessage(
                content = buildGreetingMessage(),
                isFromUser = false
            )
        )
    }

    fun checkServer() {
        viewModelScope.launch {
            when (val result = repository.checkService()) {
                is HealthResult.Success -> {
                    isServerOnline = true
                    errorMessage = null
                }

                is HealthResult.Failure -> {
                    isServerOnline = false
                    errorMessage = result.error.message
                }
            }
        }
    }

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank() || isLoading) {
            return
        }

        val normalizedMessage = userMessage.trim()
        appendMessage(ChatMessage(content = normalizedMessage, isFromUser = true))
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            when (val result = repository.sendMessage(normalizedMessage, sessionId)) {
                is HealthResult.Success -> {
                    appendMessage(ChatMessage(content = result.value, isFromUser = false))
                }

                is HealthResult.Failure -> {
                    val fallback = result.error.message.ifBlank {
                        "暂时无法处理这条消息，请稍后重试。"
                    }
                    appendMessage(
                        ChatMessage(
                            content = fallback,
                            isFromUser = false,
                            isError = true
                        )
                    )
                    errorMessage = fallback
                }
            }
            isLoading = false
        }
    }

    fun clearError() {
        errorMessage = null
    }

    fun clearChat() {
        messages.clear()
        appendMessage(
            ChatMessage(
                content = "聊天记录已清空，你可以继续咨询 ECG/PPG 指标问题。",
                isFromUser = false
            )
        )
    }

    private fun buildGreetingMessage(): String {
        return """
            你好，我是你的 ECG+PPG 健康评估助手。
            
            我可以帮你：
            - 解释 ECG/PPG 联合指标和风险提示
            - 说明 HR、HRV、SpO₂、PAT/PTT/PWTT 的含义
            - 输出可复述的趋势摘要与复测建议
            
            提示：AI 输出仅用于健康提示，不替代医疗诊断。
        """.trimIndent()
    }

    private fun appendMessage(message: ChatMessage) {
        messages += message
        val overflowCount = messages.size - MAX_MESSAGES
        if (overflowCount > 0) {
            repeat(overflowCount) {
                messages.removeAt(0)
            }
        }
    }

    private companion object {
        const val MAX_MESSAGES = 200
    }
}

data class ChatMessage(
    val content: String,
    val isFromUser: Boolean,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
