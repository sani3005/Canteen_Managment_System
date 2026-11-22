package com.canteen.student.ui.owner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canteen.student.data.model.MenuItem
import com.canteen.student.data.repository.CanteenRepository
import com.canteen.student.data.repository.CanteenRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MenuUiState(
    val menuItems: List<MenuItem> = emptyList(),
    val categories: List<String> = listOf("All", "Breakfast", "Lunch", "Snacks", "Beverages"),
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAddEditDialogOpen: Boolean = false,
    val itemToEdit: MenuItem? = null
)

class MenuManagementViewModel(
    private val repository: CanteenRepository = CanteenRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MenuUiState())
    val uiState: StateFlow<MenuUiState> = _uiState.asStateFlow()

    init {
        fetchMenu()
    }

    fun fetchMenu() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getMenu()
                .onSuccess { items ->
                    _uiState.update {
                        it.copy(
                            menuItems = items,
                            isLoading = false
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.localizedMessage ?: "Failed to load menu"
                        )
                    }
                }
        }
    }

    fun setSelectedCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleAvailability(itemId: String, currentAvailability: Boolean) {
        viewModelScope.launch {
            _uiState.update { state ->
                val updatedList = state.menuItems.map {
                    if (it.id == itemId) it.copy(isAvailable = !currentAvailability) else it
                }
                state.copy(menuItems = updatedList)
            }
            repository.toggleAvailability(itemId, !currentAvailability)
                .onFailure {
                    fetchMenu()
                }
        }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(isAddEditDialogOpen = true, itemToEdit = null) }
    }

    fun openEditDialog(item: MenuItem) {
        _uiState.update { it.copy(isAddEditDialogOpen = true, itemToEdit = item) }
    }

    fun closeAddEditDialog() {
        _uiState.update { it.copy(isAddEditDialogOpen = false, itemToEdit = null) }
    }

    fun saveMenuItem(item: MenuItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = if (item.itemId != 0) {
                repository.updateMenuItem(item)
            } else {
                repository.addMenuItem(item)
            }

            result.onSuccess {
                closeAddEditDialog()
                fetchMenu()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.localizedMessage ?: "Failed to save item"
                    )
                }
            }
        }
    }

    fun deleteMenuItem(itemId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.deleteMenuItem(itemId)
                .onSuccess {
                    fetchMenu()
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.localizedMessage ?: "Failed to delete item"
                        )
                    }
                }
        }
    }
}
