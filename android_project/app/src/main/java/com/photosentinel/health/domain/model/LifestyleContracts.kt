package com.photosentinel.health.domain.model

data class HealthPlan(
    val id: String,
    val title: String,
    val description: String,
    val time: String,
    val category: PlanCategory,
    val isCompleted: Boolean = false
)

enum class PlanCategory {
    EXERCISE,
    DIET,
    SLEEP,
    MEDICATION,
    CHECKUP
}

data class MallProduct(
    val id: String,
    val name: String,
    val description: String,
    val price: String,
    val tag: String = ""
)
