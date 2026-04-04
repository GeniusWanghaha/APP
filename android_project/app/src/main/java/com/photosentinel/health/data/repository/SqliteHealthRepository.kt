package com.photosentinel.health.data.repository

import android.content.ContentValues
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.photosentinel.health.domain.model.ChatEntry
import com.photosentinel.health.domain.model.ChatRole
import com.photosentinel.health.domain.model.HealthMetrics
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.PersistenceError
import com.photosentinel.health.domain.model.SafetyLevel
import com.photosentinel.health.domain.model.StandardizedDetection
import com.photosentinel.health.domain.model.StoredRecord
import com.photosentinel.health.domain.model.UserProfile
import com.photosentinel.health.domain.repository.HealthRepository
import com.photosentinel.health.infrastructure.db.HealthDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class SqliteHealthRepository(
    private val dbHelper: HealthDbHelper,
    private val gson: Gson = Gson()
) : HealthRepository {
    override suspend fun saveRecord(record: StoredRecord): HealthResult<Long> = withContext(Dispatchers.IO) {
        databaseCall {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("timestamp", record.detection.timestamp)
                put("user_age", record.detection.userProfile.age)
                put("user_gender", record.detection.userProfile.gender)
                put("pwv_m_s", record.detection.metrics.pwvMs)
                put("heart_rate", record.detection.metrics.heartRate)
                put("spo2_percent", record.detection.metrics.spo2Percent)
                put("elasticity_score", record.detection.metrics.elasticityScore)
                put("vascular_age", record.detection.metrics.vascularAge)
                put("rise_time_ms", record.detection.metrics.riseTimeMs)
                put("reflection_index", record.detection.metrics.reflectionIndex)
                put("signal_quality", record.detection.metrics.signalQuality)
                put("safety_level", record.safetyLevel.name)
                put("warnings_json", gson.toJson(record.warnings))
                put("ai_report", record.report)
                put("session_id", record.sessionId)
                put("algorithm_version", record.algorithmVersion)
                put("model_version", record.modelVersion)
                put("output_tier", record.outputTier)
                put("created_at", record.createdAt.toString())
            }

            db.insertOrThrow("detection_records", null, values)
        }
    }

    override suspend fun recentRecords(limit: Int): HealthResult<List<StoredRecord>> = withContext(Dispatchers.IO) {
        databaseCall {
            val db = dbHelper.readableDatabase
            val safeLimit = limit.coerceAtLeast(0)
            val cursor = db.query(
                "detection_records",
                null,
                null,
                null,
                null,
                null,
                "id DESC",
                safeLimit.toString()
            )

            cursor.use {
                val items = mutableListOf<StoredRecord>()
                while (it.moveToNext()) {
                    items += it.toRecord()
                }
                items.reversed()
            }
        }
    }

    override suspend fun allRecords(): HealthResult<List<StoredRecord>> = withContext(Dispatchers.IO) {
        databaseCall {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                "detection_records",
                null,
                null,
                null,
                null,
                null,
                "id ASC"
            )

            cursor.use {
                val items = mutableListOf<StoredRecord>()
                while (it.moveToNext()) {
                    items += it.toRecord()
                }
                items
            }
        }
    }

    override suspend fun saveChatEntry(
        sessionId: String,
        entry: ChatEntry
    ): HealthResult<Unit> = withContext(Dispatchers.IO) {
        databaseCall {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("session_id", sessionId)
                put("role", entry.role.name)
                put("content", entry.content)
                put("created_at", entry.timestamp.toString())
            }
            db.insertOrThrow("chat_history", null, values)
            Unit
        }
    }

    override suspend fun readChatHistory(
        sessionId: String,
        limit: Int
    ): HealthResult<List<ChatEntry>> = withContext(Dispatchers.IO) {
        databaseCall {
            val db = dbHelper.readableDatabase
            val safeLimit = limit.coerceAtLeast(0)
            val cursor = db.query(
                "chat_history",
                arrayOf("role", "content", "created_at"),
                "session_id = ?",
                arrayOf(sessionId),
                null,
                null,
                "id DESC",
                safeLimit.toString()
            )

            cursor.use {
                val items = mutableListOf<ChatEntry>()
                while (it.moveToNext()) {
                    val role = runCatching {
                        ChatRole.valueOf(it.getString(it.getColumnIndexOrThrow("role")))
                    }.getOrElse {
                        ChatRole.ASSISTANT
                    }

                    val content = it.getString(it.getColumnIndexOrThrow("content")).orEmpty()
                    val timestamp = parseInstant(it.getString(it.getColumnIndexOrThrow("created_at")))
                    items += ChatEntry(role = role, content = content, timestamp = timestamp)
                }
                items.reversed()
            }
        }
    }

    private fun android.database.Cursor.toRecord(): StoredRecord {
        val id = getLong(getColumnIndexOrThrow("id"))
        val timestamp = getString(getColumnIndexOrThrow("timestamp")).orEmpty()
        val userAge = getIntOrDefault("user_age", 25)
        val userGender = getString(getColumnIndexOrThrow("user_gender")).orEmpty().ifBlank { "未知" }
        val pwvMs = getDoubleOrDefault("pwv_m_s", 0.0)
        val heartRate = getIntOrDefault("heart_rate", 0)
        val spo2Percent = getDoubleOrDefault("spo2_percent", 0.0)
        val elasticityScore = getIntOrDefault("elasticity_score", 0)
        val vascularAge = getIntOrDefault("vascular_age", 0)
        val riseTimeMs = getIntOrDefault("rise_time_ms", 0)
        val reflectionIndex = getDoubleOrDefault("reflection_index", 0.0)
        val signalQuality = getDoubleOrDefault("signal_quality", 0.0)
        val safetyLevel = runCatching {
            SafetyLevel.valueOf(getString(getColumnIndexOrThrow("safety_level")).orEmpty())
        }.getOrElse { SafetyLevel.OK }
        val warnings = parseWarnings(getString(getColumnIndexOrThrow("warnings_json")).orEmpty())
        val report = getString(getColumnIndexOrThrow("ai_report")).orEmpty()
        val sessionId = getStringByNameOrNull("session_id")
        val algorithmVersion = getStringByNameOrNull("algorithm_version").orEmpty().ifBlank {
            "algo-v2.0"
        }
        val modelVersion = getStringByNameOrNull("model_version").orEmpty().ifBlank {
            "llm-v2.0"
        }
        val outputTier = getStringByNameOrNull("output_tier").orEmpty().ifBlank {
            "BASELINE"
        }
        val createdAt = parseInstant(getString(getColumnIndexOrThrow("created_at")).orEmpty())

        return StoredRecord(
            id = id,
            detection = StandardizedDetection(
                timestamp = timestamp,
                userProfile = UserProfile(
                    age = userAge,
                    gender = userGender
                ),
                metrics = HealthMetrics(
                    pwvMs = pwvMs,
                    heartRate = heartRate,
                    spo2Percent = spo2Percent,
                    elasticityScore = elasticityScore,
                    vascularAge = vascularAge,
                    riseTimeMs = riseTimeMs,
                    reflectionIndex = reflectionIndex,
                    signalQuality = signalQuality
                )
            ),
            safetyLevel = safetyLevel,
            warnings = warnings,
            report = report,
            sessionId = sessionId,
            algorithmVersion = algorithmVersion,
            modelVersion = modelVersion,
            outputTier = outputTier,
            createdAt = createdAt
        )
    }

    private fun android.database.Cursor.getIntOrDefault(
        column: String,
        defaultValue: Int
    ): Int {
        val index = getColumnIndex(column)
        if (index == -1 || isNull(index)) {
            return defaultValue
        }
        return getInt(index)
    }

    private fun android.database.Cursor.getDoubleOrDefault(
        column: String,
        defaultValue: Double
    ): Double {
        val index = getColumnIndex(column)
        if (index == -1 || isNull(index)) {
            return defaultValue
        }
        return getDouble(index)
    }

    private fun android.database.Cursor.getStringByNameOrNull(column: String): String? {
        val index = getColumnIndex(column)
        if (index == -1 || isNull(index)) {
            return null
        }
        return getString(index)
    }

    private fun parseWarnings(raw: String): List<String> {
        if (raw.isBlank()) {
            return emptyList()
        }
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type) ?: emptyList()
        }.getOrElse {
            emptyList()
        }
    }

    private fun parseInstant(raw: String): Instant {
        return runCatching { Instant.parse(raw) }.getOrElse { Instant.now() }
    }

    private inline fun <T> databaseCall(block: () -> T): HealthResult<T> {
        return try {
            HealthResult.Success(block())
        } catch (exception: Exception) {
            HealthResult.Failure(
                PersistenceError(exception.message ?: "本地数据库操作失败")
            )
        }
    }
}
