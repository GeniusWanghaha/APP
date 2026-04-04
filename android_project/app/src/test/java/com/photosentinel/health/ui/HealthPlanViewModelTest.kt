package com.photosentinel.health.ui

import com.photosentinel.health.MainDispatcherRule
import com.photosentinel.health.domain.model.HealthPlan
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.MallProduct
import com.photosentinel.health.domain.model.PlanCategory
import com.photosentinel.health.domain.model.ValidationError
import com.photosentinel.health.presentation.repository.LifestyleCatalogDataSource
import com.photosentinel.health.ui.viewmodel.HealthPlanViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthPlanViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun refresh_loadsPlansFromRepository() = runTest {
        val fake = FakeLifestyleCatalogDataSource()
        val viewModel = HealthPlanViewModel(repository = fake)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.isLoading)
        assertEquals(2, viewModel.uiState.plans.size)
        assertEquals("p1", viewModel.uiState.plans.first().id)
    }

    @Test
    fun togglePlan_success_updatesCompletionAndPersists() = runTest {
        val fake = FakeLifestyleCatalogDataSource()
        val viewModel = HealthPlanViewModel(repository = fake)
        advanceUntilIdle()

        viewModel.togglePlan("p1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.plans.first { it.id == "p1" }.isCompleted)
        assertEquals("p1" to true, fake.lastUpdate)
        assertEquals(null, viewModel.uiState.errorMessage)
    }

    @Test
    fun togglePlan_failure_rollsBackAndShowsError() = runTest {
        val fake = FakeLifestyleCatalogDataSource(updateShouldFail = true)
        val viewModel = HealthPlanViewModel(repository = fake)
        advanceUntilIdle()

        viewModel.togglePlan("p1")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.plans.first { it.id == "p1" }.isCompleted)
        assertEquals("持久化失败", viewModel.uiState.errorMessage)
    }
}

private class FakeLifestyleCatalogDataSource(
    private val updateShouldFail: Boolean = false
) : LifestyleCatalogDataSource {
    private val plans = mutableListOf(
        HealthPlan(
            id = "p1",
            title = "晨跑训练",
            description = "慢跑 30 分钟",
            time = "07:00",
            category = PlanCategory.EXERCISE,
            isCompleted = false
        ),
        HealthPlan(
            id = "p2",
            title = "低盐午餐",
            description = "减少钠摄入",
            time = "12:00",
            category = PlanCategory.DIET,
            isCompleted = false
        )
    )

    var lastUpdate: Pair<String, Boolean>? = null

    override suspend fun healthPlans(): HealthResult<List<HealthPlan>> {
        return HealthResult.Success(plans.toList())
    }

    override suspend fun mallProducts(): HealthResult<List<MallProduct>> {
        return HealthResult.Success(emptyList())
    }

    override suspend fun updateHealthPlanCompletion(
        planId: String,
        isCompleted: Boolean
    ): HealthResult<Unit> {
        if (updateShouldFail) {
            return HealthResult.Failure(ValidationError("持久化失败"))
        }

        lastUpdate = planId to isCompleted
        val index = plans.indexOfFirst { it.id == planId }
        if (index >= 0) {
            plans[index] = plans[index].copy(isCompleted = isCompleted)
        }
        return HealthResult.Success(Unit)
    }
}
