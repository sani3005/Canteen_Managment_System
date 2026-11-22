package com.canteen.student.ui.owner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canteen.student.data.model.AnalyticsSummary
import com.canteen.student.data.repository.CanteenRepository
import com.canteen.student.data.repository.CanteenRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TimeFilter {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    ALL_TIME
}

data class AnalyticsUiState(
    val summary: AnalyticsSummary = AnalyticsSummary(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedTimeFilter: TimeFilter = TimeFilter.TODAY,
    val selectedDayOfWeek: String = "Monday"
)

class AnalyticsViewModel(
    private val repository: CanteenRepository = CanteenRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        fetchAnalytics()
    }

    fun fetchAnalytics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getAnalytics()
                .onSuccess { summaryData ->
                    _uiState.update {
                        it.copy(
                            summary = summaryData,
                            isLoading = false
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.localizedMessage ?: "Failed to load analytics"
                        )
                    }
                }
        }
    }

    fun setTimeFilter(filter: TimeFilter) {
        _uiState.update { it.copy(selectedTimeFilter = filter) }
        fetchAnalytics()
    }

    fun setSelectedDayOfWeek(day: String) {
        _uiState.update { it.copy(selectedDayOfWeek = day) }
    }
}
