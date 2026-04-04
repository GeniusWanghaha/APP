package com.photosentinel.health.ui

import com.photosentinel.health.MainDispatcherRule
import com.photosentinel.health.domain.model.DetectionInput
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.NotEnoughHistoryError
import com.photosentinel.health.domain.model.SingleAnalysis
import com.photosentinel.health.domain.model.StoredRecord
import com.photosentinel.health.domain.model.TrendAnalysis
import com.photosentinel.health.domain.model.ValidationError
import com.photosentinel.health.presentation.repository.HealthAssistantDataSource
import com.photosentinel.health.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_checksServiceAndAddsGreeting() = runTest {
        val fake = FakeHealthAssistantDataSource()
        val viewModel = ChatViewModel(repository = fake)

        advanceUntilIdle()

        assertTrue(viewModel.isServerOnline)
        assertEquals(1, viewModel.messages.size)
        assertTrue(viewModel.messages.first().content.contains("ECG+PPG"))
    }

    @Test
    fun sendMessage_success_appendsAssistantReply() = runTest {
        val fake = FakeHealthAssistantDataSource(
            sendMessageResult = { message -> HealthResult.Success("echo:$message") }
        )
        val viewModel = ChatViewModel(repository = fake)
        advanceUntilIdle()

        viewModel.sendMessage("  解释一下脉搏波速度  ")
        advanceUntilIdle()

        assertFalse(viewModel.isLoading)
        assertEquals(null, viewModel.errorMessage)
        assertEquals("解释一下脉搏波速度", fake.sentMessages.last())
        assertEquals("echo:解释一下脉搏波速度", viewModel.messages.last().content)
        assertFalse(viewModel.messages.last().isFromUser)
    }

    @Test
    fun sendMessage_failure_setsErrorAndErrorBubble() = runTest {
        val fake = FakeHealthAssistantDataSource(
            sendMessageResult = {
                HealthResult.Failure(ValidationError("服务暂不可用"))
            }
        )
        val viewModel = ChatViewModel(repository = fake)
        advanceUntilIdle()

        viewModel.sendMessage("你好")
        advanceUntilIdle()

        assertEquals("服务暂不可用", viewModel.errorMessage)
        assertTrue(viewModel.messages.last().isError)
        assertEquals("服务暂不可用", viewModel.messages.last().content)
    }

    @Test
    fun sendMessage_overLimit_trimsOldestMessages() = runTest {
        val fake = FakeHealthAssistantDataSource(
            sendMessageResult = { HealthResult.Success("ok") }
        )
        val viewModel = ChatViewModel(repository = fake)
        advanceUntilIdle()

        repeat(120) { index ->
            viewModel.sendMessage("m$index")
            advanceUntilIdle()
        }

        assertEquals(200, viewModel.messages.size)
        assertFalse(viewModel.messages.first().content.contains("ECG+PPG"))
        assertNotNull(viewModel.messages.last().content)
    }
}

private class FakeHealthAssistantDataSource(
    private val checkServiceResult: HealthResult<Unit> = HealthResult.Success(Unit),
    private val sendMessageResult: (String) -> HealthResult<String> = {
        HealthResult.Success("ok")
    }
) : HealthAssistantDataSource {
    val sentMessages = mutableListOf<String>()

    override suspend fun checkService(): HealthResult<Unit> {
        return checkServiceResult
    }

    override suspend fun sendMessage(
        message: String,
        sessionId: String
    ): HealthResult<String> {
        sentMessages += message
        return sendMessageResult(message)
    }

    override suspend fun analyzeMeasurement(input: DetectionInput): HealthResult<SingleAnalysis> {
        return HealthResult.Failure(ValidationError("该测试未使用此分支"))
    }

    override suspend fun analyzeTrend(): HealthResult<TrendAnalysis> {
        return HealthResult.Failure(NotEnoughHistoryError("该测试未使用此分支"))
    }

    override suspend fun recentRecords(limit: Int): HealthResult<List<StoredRecord>> {
        return HealthResult.Success(emptyList())
    }
}
