package com.photosentinel.health.data.repository

import android.content.ContentValues
import android.content.Context
import com.google.gson.Gson
import com.photosentinel.health.domain.model.ExportFormat
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.MeasurementSession
import com.photosentinel.health.domain.model.PersistenceError
import com.photosentinel.health.domain.model.SessionEvent
import com.photosentinel.health.domain.model.SessionEventType
import com.photosentinel.health.domain.model.SessionQualityGrade
import com.photosentinel.health.domain.repository.SessionAuditRepository
import com.photosentinel.health.infrastructure.db.HealthDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

class SqliteSessionAuditRepository(
    context: Context,
    private val dbHelper: HealthDbHelper,
    private val gson: Gson = Gson()
) : SessionAuditRepository {
    private val appContext = context.applicationContext

    override suspend fun startSession(session: MeasurementSession): HealthResult<Unit> = withContext(
        Dispatchers.IO
    ) {
        databaseCall {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("session_id", session.sessionId)
                put("device_id", session.deviceId)
                put("firmware_version", session.firmwareVersion)
                put("ecg_sample_rate_hz", session.ecgSampleRateHz)
                put("ppg_sample_rate_hz", session.ppgSampleRateHz)
                put("started_at", session.startedAt.toString())
                put("ended_at", session.endedAt?.toString())
                put("quality_grade", session.qualityGrade.name)
                put("dropped_frame_count", session.droppedFrameCount)
                put("algorithm_version", session.algorithmVersion)
                put("model_version", session.modelVersion)
                put("notes", session.notes)
                put("updated_at", Instant.now().toString())
            }
            db.insertWithOnConflict(
                "measurement_sessions",
                null,
                values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )
            Unit
        }
    }

    override suspend fun finishSession(session: MeasurementSession): HealthResult<Unit> = withContext(
        Dispatchers.IO
    ) {
        databaseCall {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("ended_at", session.endedAt?.toString() ?: Instant.now().toString())
                put("quality_grade", session.qualityGrade.name)
                put("dropped_frame_count", session.droppedFrameCount)
                put("algorithm_version", session.algorithmVersion)
                put("model_version", session.modelVersion)
                put("notes", session.notes)
                put("updated_at", Instant.now().toString())
            }
            db.update(
                "measurement_sessions",
                values,
                "session_id = ?",
                arrayOf(session.sessionId)
            )
            Unit
        }
    }

    override suspend fun appendEvent(event: SessionEvent): HealthResult<Unit> = withContext(Dispatchers.IO) {
        databaseCall {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("session_id", event.sessionId)
                put("event_type", event.eventType.name)
                put("message", event.message)
                put("payload_json", event.payloadJson)
                put("created_at", event.createdAt.toString())
            }
            db.insertOrThrow("session_events", null, values)
            Unit
        }
    }

    override suspend fun recentSessions(limit: Int): HealthResult<List<MeasurementSession>> = withContext(
        Dispatchers.IO
    ) {
        databaseCall {
            val db = dbHelper.readableDatabase
            val safeLimit = limit.coerceAtLeast(1)
            val cursor = db.query(
                "measurement_sessions",
                null,
                null,
                null,
                null,
                null,
                "started_at DESC",
                safeLimit.toString()
            )
            cursor.use {
                val sessions = mutableListOf<MeasurementSession>()
                while (it.moveToNext()) {
                    sessions += it.toSession()
                }
                sessions
            }
        }
    }

    override suspend fun exportSession(
        sessionId: String,
        format: ExportFormat
    ): HealthResult<String> = withContext(Dispatchers.IO) {
        databaseCall {
            val db = dbHelper.readableDatabase
            val sessionCursor = db.query(
                "measurement_sessions",
                null,
                "session_id = ?",
                arrayOf(sessionId),
                null,
                null,
                null,
                "1"
            )
            val session = sessionCursor.use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    cursor.toSession()
                }
            } ?: throw IllegalStateException("会话不存在：$sessionId")

            val eventCursor = db.query(
                "session_events",
                null,
                "session_id = ?",
                arrayOf(sessionId),
                null,
                null,
                "created_at ASC"
            )

            val events = eventCursor.use { cursor ->
                val list = mutableListOf<SessionEvent>()
                while (cursor.moveToNext()) {
                    list += SessionEvent(
                        sessionId = sessionId,
                        eventType = runCatching {
                            SessionEventType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("event_type")))
                        }.getOrElse { SessionEventType.ERROR },
                        message = cursor.getString(cursor.getColumnIndexOrThrow("message")).orEmpty(),
                        payloadJson = cursor.getString(cursor.getColumnIndexOrThrow("payload_json")),
                        createdAt = parseInstant(cursor.getString(cursor.getColumnIndexOrThrow("created_at")))
                    )
                }
                list
            }

            val exportDir = File(appContext.filesDir, "exports").apply { mkdirs() }
            val extension = if (format == ExportFormat.CSV) "csv" else "json"
            val file = File(exportDir, "session_${sessionId}_${System.currentTimeMillis()}.$extension")

            if (format == ExportFormat.CSV) {
                file.writeText(
                    buildString {
                        appendLine("session_id,event_type,message,created_at,payload_json")
                        events.forEach { event ->
                            val escapedMessage = event.message.replace("\"", "\"\"")
                            val escapedPayload = event.payloadJson.orEmpty().replace("\"", "\"\"")
                            appendLine(
                                "\"${event.sessionId}\",\"${event.eventType.name}\",\"$escapedMessage\",\"${event.createdAt}\",\"$escapedPayload\""
                            )
                        }
                    }
                )
            } else {
                val payload = mapOf(
                    "session" to mapOf(
                        "sessionId" to session.sessionId,
                        "deviceId" to session.deviceId,
                        "firmwareVersion" to session.firmwareVersion,
                        "ecgSampleRateHz" to session.ecgSampleRateHz,
                        "ppgSampleRateHz" to session.ppgSampleRateHz,
                        "startedAt" to session.startedAt.toString(),
                        "endedAt" to session.endedAt?.toString(),
                        "qualityGrade" to session.qualityGrade.name,
                        "droppedFrameCount" to session.droppedFrameCount,
                        "algorithmVersion" to session.algorithmVersion,
                        "modelVersion" to session.modelVersion,
                        "notes" to session.notes
                    ),
                    "events" to events.map { event ->
                        mapOf(
                            "sessionId" to event.sessionId,
                            "eventType" to event.eventType.name,
                            "message" to event.message,
                            "payloadJson" to event.payloadJson,
                            "createdAt" to event.createdAt.toString()
                        )
                    }
                )
                file.writeText(gson.toJson(payload))
            }

            file.absolutePath
        }
    }

    private fun android.database.Cursor.toSession(): MeasurementSession {
        return MeasurementSession(
            sessionId = getString(getColumnIndexOrThrow("session_id")).orEmpty(),
            deviceId = getString(getColumnIndexOrThrow("device_id")).orEmpty(),
            firmwareVersion = getString(getColumnIndexOrThrow("firmware_version")),
            ecgSampleRateHz = getIntOrDefault("ecg_sample_rate_hz", 250),
            ppgSampleRateHz = getIntOrDefault("ppg_sample_rate_hz", 400),
            startedAt = parseInstant(getString(getColumnIndexOrThrow("started_at"))),
            endedAt = getString(getColumnIndexOrThrow("ended_at"))?.let(::parseInstant),
            qualityGrade = runCatching {
                SessionQualityGrade.valueOf(getString(getColumnIndexOrThrow("quality_grade")).orEmpty())
            }.getOrElse { SessionQualityGrade.INVALID },
            droppedFrameCount = getIntOrDefault("dropped_frame_count", 0),
            algorithmVersion = getString(getColumnIndexOrThrow("algorithm_version")).orEmpty().ifBlank {
                "algo-v2.0"
            },
            modelVersion = getString(getColumnIndexOrThrow("model_version")).orEmpty().ifBlank {
                "llm-v2.0"
            },
            notes = getString(getColumnIndexOrThrow("notes"))
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

    private fun parseInstant(raw: String?): Instant {
        return runCatching { Instant.parse(raw) }.getOrElse { Instant.now() }
    }

    private inline fun <T> databaseCall(block: () -> T): HealthResult<T> {
        return try {
            HealthResult.Success(block())
        } catch (exception: Exception) {
            HealthResult.Failure(
                PersistenceError(exception.message ?: "会话数据持久化失败")
            )
        }
    }
}
