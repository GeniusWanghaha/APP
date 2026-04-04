package com.photosentinel.health.infrastructure.llm

data class LlmMessage(
    val role: String,
    val content: String
)

internal data class ChatCompletionRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val temperature: Double,
    val max_tokens: Int
)

internal data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList()
) {
    internal data class Choice(
        val message: Message = Message()
    )

    internal data class Message(
        val content: String = ""
    )
}
