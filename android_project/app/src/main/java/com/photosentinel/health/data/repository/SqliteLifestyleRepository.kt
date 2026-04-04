package com.photosentinel.health.data.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.photosentinel.health.domain.model.HealthPlan
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.MallProduct
import com.photosentinel.health.domain.model.PlanCategory
import com.photosentinel.health.domain.model.UnexpectedError
import com.photosentinel.health.domain.model.ValidationError
import com.photosentinel.health.domain.repository.LifestyleRepository
import com.photosentinel.health.infrastructure.db.HealthDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class SqliteLifestyleRepository(
    private val dbHelper: HealthDbHelper
) : LifestyleRepository {
    override suspend fun readHealthPlans(): HealthResult<List<HealthPlan>> = withContext(Dispatchers.IO) {
        safeCall {
            val completion = readCompletionMap()
            HEALTH_PLANS.map { plan ->
                val isCompleted = completion[plan.id] ?: false
                plan.copy(isCompleted = isCompleted)
            }
        }
    }

    override suspend fun readMallProducts(): HealthResult<List<MallProduct>> = withContext(Dispatchers.IO) {
        safeCall { MALL_PRODUCTS }
    }

    override suspend fun updateHealthPlanCompletion(
        planId: String,
        isCompleted: Boolean
    ): HealthResult<Unit> = withContext(Dispatchers.IO) {
        if (planId.isBlank()) {
            return@withContext HealthResult.Failure(ValidationError("计划编号不能为空"))
        }

        safeCall {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("plan_id", planId)
                put("is_completed", if (isCompleted) 1 else 0)
                put("updated_at", Instant.now().toString())
            }
            db.insertWithOnConflict(
                "health_plan_state",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            Unit
        }
    }

    private fun readCompletionMap(): Map<String, Boolean> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "health_plan_state",
            arrayOf("plan_id", "is_completed"),
            null,
            null,
            null,
            null,
            null
        )

        cursor.use {
            val result = mutableMapOf<String, Boolean>()
            while (it.moveToNext()) {
                val planId = it.getString(it.getColumnIndexOrThrow("plan_id")).orEmpty()
                if (planId.isBlank()) {
                    continue
                }
                val isCompleted = it.getInt(it.getColumnIndexOrThrow("is_completed")) == 1
                result[planId] = isCompleted
            }
            return result
        }
    }

    private inline fun <T> safeCall(block: () -> T): HealthResult<T> {
        return try {
            HealthResult.Success(block())
        } catch (exception: Exception) {
            HealthResult.Failure(
                UnexpectedError(
                    message = exception.message ?: "生活方式模块数据操作失败",
                    cause = exception
                )
            )
        }
    }

    private companion object {
        val HEALTH_PLANS: List<HealthPlan> = listOf(
            HealthPlan(
                id = "p1",
                title = "装载手机壳并连接手机",
                description = "验证无电池、一体化形态，确认 Type-C/OTG 供电正常",
                time = "步骤 1",
                category = PlanCategory.CHECKUP
            ),
            HealthPlan(
                id = "p2",
                title = "打开 APP 并连接 BLE",
                description = "验证即连即用与数据链路稳定性",
                time = "步骤 2",
                category = PlanCategory.CHECKUP
            ),
            HealthPlan(
                id = "p3",
                title = "接触 ECG 与 PPG 区域",
                description = "指尖触碰电极与透光窗，进入同步采集状态",
                time = "步骤 3",
                category = PlanCategory.EXERCISE
            ),
            HealthPlan(
                id = "p4",
                title = "实时采集双波形与指标",
                description = "输出 ECG/PPG 时序、HR、SpO2、PTT/PWTT 等指标",
                time = "步骤 4",
                category = PlanCategory.DIET
            ),
            HealthPlan(
                id = "p5",
                title = "结束采集并生成 AI 报告",
                description = "输出状态摘要、风险提示与后续监测建议",
                time = "步骤 5",
                category = PlanCategory.SLEEP
            )
        )

        val MALL_PRODUCTS: List<MallProduct> = listOf(
            MallProduct(
                id = "m1",
                name = "硬件层",
                description = "ESP32-S3 + AD8232 + MAX30102，负责 ECG/PPG 原始同步采集",
                price = "采集层",
                tag = "核心"
            ),
            MallProduct(
                id = "m2",
                name = "通信层",
                description = "BLE 5.0 自定义服务，发送 FrameID、时间戳、状态字与 CRC",
                price = "链路层",
                tag = "必需"
            ),
            MallProduct(
                id = "m3",
                name = "算法层",
                description = "手机端滤波、峰值检测、节律分析、PTT/PWTT 与趋势统计",
                price = "计算层",
                tag = "重点"
            ),
            MallProduct(
                id = "m4",
                name = "AI 层",
                description = "对结构化指标做风险解释、趋势总结和自然语言问答",
                price = "解释层",
                tag = "分析"
            )
        )
    }
}
