package com.photosentinel.health.infrastructure.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HealthDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        createDetectionTable(db)
        createChatTable(db)
        createHealthPlanTable(db)
        createMeasurementSessionTable(db)
        createSessionEventTable(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            createChatTable(db)
            createHealthPlanTable(db)
        }

        if (oldVersion < 3) {
            createMeasurementSessionTable(db)
            createSessionEventTable(db)
            ensureDetectionColumns(db)
        }
    }

    private fun createDetectionTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS detection_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL,
                user_age INTEGER,
                user_gender TEXT,
                pwv_m_s REAL,
                heart_rate INTEGER,
                spo2_percent REAL,
                elasticity_score INTEGER,
                vascular_age INTEGER,
                rise_time_ms INTEGER,
                reflection_index REAL,
                signal_quality REAL,
                safety_level TEXT NOT NULL,
                warnings_json TEXT NOT NULL,
                ai_report TEXT NOT NULL,
                session_id TEXT,
                algorithm_version TEXT DEFAULT 'algo-v2.0',
                model_version TEXT DEFAULT 'llm-v2.0',
                output_tier TEXT DEFAULT 'BASELINE',
                created_at TEXT NOT NULL
            )
            """.trimIndent()
        )
        ensureDetectionColumns(db)
    }

    private fun ensureDetectionColumns(db: SQLiteDatabase) {
        runSqlSafely(db, "ALTER TABLE detection_records ADD COLUMN session_id TEXT")
        runSqlSafely(db, "ALTER TABLE detection_records ADD COLUMN algorithm_version TEXT DEFAULT 'algo-v2.0'")
        runSqlSafely(db, "ALTER TABLE detection_records ADD COLUMN model_version TEXT DEFAULT 'llm-v2.0'")
        runSqlSafely(db, "ALTER TABLE detection_records ADD COLUMN output_tier TEXT DEFAULT 'BASELINE'")
    }

    private fun createChatTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_chat_history_session_id_id
            ON chat_history(session_id, id DESC)
            """.trimIndent()
        )
    }

    private fun createHealthPlanTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS health_plan_state (
                plan_id TEXT PRIMARY KEY,
                is_completed INTEGER NOT NULL,
                updated_at TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createMeasurementSessionTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS measurement_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL UNIQUE,
                device_id TEXT NOT NULL,
                firmware_version TEXT,
                ecg_sample_rate_hz INTEGER NOT NULL,
                ppg_sample_rate_hz INTEGER NOT NULL,
                started_at TEXT NOT NULL,
                ended_at TEXT,
                quality_grade TEXT NOT NULL,
                dropped_frame_count INTEGER NOT NULL DEFAULT 0,
                algorithm_version TEXT NOT NULL DEFAULT 'algo-v2.0',
                model_version TEXT NOT NULL DEFAULT 'llm-v2.0',
                notes TEXT,
                updated_at TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_measurement_sessions_started_at
            ON measurement_sessions(started_at DESC)
            """.trimIndent()
        )
    }

    private fun createSessionEventTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS session_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                event_type TEXT NOT NULL,
                message TEXT NOT NULL,
                payload_json TEXT,
                created_at TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_session_events_session_id_created_at
            ON session_events(session_id, created_at ASC)
            """.trimIndent()
        )
    }

    private fun runSqlSafely(db: SQLiteDatabase, sql: String) {
        runCatching { db.execSQL(sql) }
    }

    private companion object {
        const val DATABASE_NAME = "health_agent.db"
        const val DATABASE_VERSION = 3
    }
}
