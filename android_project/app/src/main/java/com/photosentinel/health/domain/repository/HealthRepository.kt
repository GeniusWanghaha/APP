package com.photosentinel.health.domain.repository

import com.photosentinel.health.domain.model.ChatEntry
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.StoredRecord

interface HealthRepository {
    suspend fun saveRecord(record: StoredRecord): HealthResult<Long>

    suspend fun recentRecords(limit: Int): HealthResult<List<StoredRecord>>

    suspend fun allRecords(): HealthResult<List<StoredRecord>>

    suspend fun saveChatEntry(sessionId: String, entry: ChatEntry): HealthResult<Unit>

    suspend fun readChatHistory(sessionId: String, limit: Int = 20): HealthResult<List<ChatEntry>>
}
