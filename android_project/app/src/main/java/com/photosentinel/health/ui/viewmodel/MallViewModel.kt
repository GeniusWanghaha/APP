package com.photosentinel.health.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.MallProduct
import com.photosentinel.health.presentation.repository.LifestyleCatalogDataSource
import com.photosentinel.health.presentation.repository.LifestyleCatalogRepository
import kotlinx.coroutines.launch

data class MallUiState(
    val products: List<MallProduct> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class MallViewModel(
    private val repository: LifestyleCatalogDataSource = LifestyleCatalogRepository()
) : ViewModel() {

    var uiState by mutableStateOf(MallUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            when (val result = repository.mallProducts()) {
                is HealthResult.Success -> {
                    uiState = MallUiState(
                        products = result.value,
                        isLoading = false,
                        errorMessage = null
                    )
                }

                is HealthResult.Failure -> {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = result.error.message
                    )
                }
            }
        }
    }
}
