package com.photosentinel.health.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photosentinel.health.domain.model.HealthPlan
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.presentation.repository.LifestyleCatalogDataSource
import com.photosentinel.health.presentation.repository.LifestyleCatalogRepository
import kotlinx.coroutines.launch

data class HealthPlanUiState(
    val plans: List<HealthPlan> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class HealthPlanViewModel(
    private val repository: LifestyleCatalogDataSource = LifestyleCatalogRepository()
) : ViewModel() {
    private val mutablePlans = mutableStateListOf<HealthPlan>()

    var uiState by mutableStateOf(HealthPlanUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            when (val result = repository.healthPlans()) {
                is HealthResult.Success -> {
                    mutablePlans.clear()
                    mutablePlans.addAll(result.value)
                    uiState = HealthPlanUiState(
                        plans = mutablePlans,
                        isLoading = false,
                        errorMessage = null
                    )
                }

                is HealthResult.Failure -> {
                    uiState = uiState.copy(
                        plans = mutablePlans,
                        isLoading = false,
                        errorMessage = result.error.message
                    )
                }
            }
        }
    }

    fun togglePlan(planId: String) {
        val index = mutablePlans.indexOfFirst { it.id == planId }
        if (index < 0) {
            return
        }
        val current = mutablePlans[index]
        val target = !current.isCompleted

        mutablePlans[index] = current.copy(isCompleted = target)
        uiState = uiState.copy(plans = mutablePlans, errorMessage = null)

        viewModelScope.launch {
            when (val result = repository.updateHealthPlanCompletion(planId, target)) {
                is HealthResult.Success -> {
                    uiState = uiState.copy(plans = mutablePlans, errorMessage = null)
                }

                is HealthResult.Failure -> {
                    mutablePlans[index] = current
                    uiState = uiState.copy(
                        plans = mutablePlans,
                        errorMessage = result.error.message
                    )
                }
            }
        }
    }

    fun setAllPlansCompleted(targetCompleted: Boolean) {
        if (mutablePlans.isEmpty()) {
            return
        }
        val previousSnapshot = mutablePlans.toList()
        val updated = mutablePlans.map { it.copy(isCompleted = targetCompleted) }
        mutablePlans.clear()
        mutablePlans.addAll(updated)
        uiState = uiState.copy(plans = mutablePlans, errorMessage = null)

        viewModelScope.launch {
            var hasFailure = false
            var lastError: String? = null
            updated.forEach { plan ->
                when (val result = repository.updateHealthPlanCompletion(plan.id, targetCompleted)) {
                    is HealthResult.Success -> Unit
                    is HealthResult.Failure -> {
                        hasFailure = true
                        lastError = result.error.message
                    }
                }
            }

            if (hasFailure) {
                mutablePlans.clear()
                mutablePlans.addAll(previousSnapshot)
                uiState = uiState.copy(
                    plans = mutablePlans,
                    errorMessage = lastError ?: "批量更新计划状态失败"
                )
            } else {
                uiState = uiState.copy(
                    plans = mutablePlans,
                    errorMessage = null
                )
            }
        }
    }
}
