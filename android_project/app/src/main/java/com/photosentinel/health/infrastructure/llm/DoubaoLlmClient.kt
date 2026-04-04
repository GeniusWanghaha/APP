package com.photosentinel.health.infrastructure.llm

import com.google.gson.Gson
import com.photosentinel.health.BuildConfig
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.ValidationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class DoubaoLlmClient(
    private val apiKey: String = BuildConfig.DOUBAO_API_KEY,
    private val endpointId: String = BuildConfig.DOUBAO_ENDPOINT_ID,
    private val baseUrl: String = BuildConfig.DOUBAO_BASE_URL,
    private val gson: Gson = Gson(),
    private val okHttpClient: OkHttpClient = defaultClient()
) {
    suspend fun chat(
        messages: List<LlmMessage>,
        temperature: Double = 0.3,
        maxTokens: Int = 1_500
    ): HealthResult<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext HealthResult.Failure(
                ValidationError("缺少豆包访问密钥配置")
            )
        }
        if (endpointId.isBlank()) {
            return@withContext HealthResult.Failure(
                ValidationError("缺少豆包端点配置")
            )
        }

        val payload = ChatCompletionRequest(
            model = endpointId,
            messages = messages,
            temperature = temperature,
            max_tokens = maxTokens
        )

        val requestBody = gson.toJson(payload)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorText = response.body?.string().orEmpty()
                    return@withContext HealthResult.Failure(
                        ValidationError("大模型请求失败（${response.code}）：$errorText")
                    )
                }

                val body = response.body?.string().orEmpty()
                val parsed = gson.fromJson(body, ChatCompletionResponse::class.java)
                val content = parsed.choices.firstOrNull()?.message?.content?.trim().orEmpty()

                return@withContext if (content.isBlank()) {
                    HealthResult.Failure(ValidationError("大模型返回内容为空"))
                } else {
                    HealthResult.Success(content)
                }
            }
        } catch (timeout: SocketTimeoutException) {
            return@withContext HealthResult.Failure(
                ValidationError("大模型请求超时，请稍后重试")
            )
        } catch (exception: Exception) {
            return@withContext HealthResult.Failure(
                ValidationError("大模型请求失败：${exception.message}")
            )
        }
    }

    suspend fun healthCheck(): HealthResult<Unit> {
        if (apiKey.isBlank()) {
            return HealthResult.Failure(ValidationError("缺少豆包访问密钥配置"))
        }
        if (endpointId.isBlank()) {
            return HealthResult.Failure(ValidationError("缺少豆包端点配置"))
        }

        val pingResult = chat(
            messages = listOf(LlmMessage(role = "user", content = "请仅回复：已连接")),
            temperature = 0.0,
            maxTokens = 16
        )

        return when (pingResult) {
            is HealthResult.Success -> HealthResult.Success(Unit)
            is HealthResult.Failure -> pingResult
        }
    }

    private companion object {
        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
