package com.photosentinel.health.data.repository

import com.photosentinel.health.domain.model.ChatEntry
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.PersistenceError
import com.photosentinel.health.domain.model.StoredRecord
import com.photosentinel.health.domain.repository.HealthRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryHealthRepository : HealthRepository {
    private val lock = Mutex()
    private var nextRecordId = 1L

    private val records = mutableListOf<StoredRecord>()
    private val chats = mutableMapOf<String, MutableList<ChatEntry>>()

    override suspend fun saveRecord(record: StoredRecord): HealthResult<Long> {
        return repositoryCall {
            lock.withLock {
                val assignedId = nextRecordId++
                records += record.copy(id = assignedId)
                assignedId
            }
        }
    }

    override suspend fun recentRecords(limit: Int): HealthResult<List<StoredRecord>> {
        return repositoryCall {
            lock.withLock {
                val safeLimit = limit.coerceAtLeast(0)
                records.takeLast(safeLimit)
            }
        }
    }

    override suspend fun allRecords(): HealthResult<List<StoredRecord>> {
        return repositoryCall {
            lock.withLock {
                records.toList()
            }
        }
    }

    override suspend fun saveChatEntry(sessionId: String, entry: ChatEntry): HealthResult<Unit> {
        return repositoryCall {
            lock.withLock {
                val session = chats.getOrPut(sessionId) { mutableListOf() }
                session += entry
            }
        }
    }

    override suspend fun readChatHistory(
        sessionId: String,
        limit: Int
    ): HealthResult<List<ChatEntry>> {
        return repositoryCall {
            lock.withLock {
                val session = chats[sessionId].orEmpty()
                session.takeLast(limit.coerceAtLeast(0))
            }
        }
    }

    private suspend inline fun <T> repositoryCall(
        crossinline block: suspend () -> T
    ): HealthResult<T> {
        return try {
            HealthResult.Success(block())
        } catch (exception: Exception) {
            HealthResult.Failure(PersistenceError(exception.message ?: "仓库调用失败"))
        }
    }
}
