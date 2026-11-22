package com.canteen.student.ui.owner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canteen.student.data.api.SessionManager
import com.canteen.student.data.model.CanteenCodeUtils
import com.canteen.student.data.model.CanteenInfo
import com.canteen.student.data.repository.CanteenRepository
import com.canteen.student.data.repository.CanteenRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CanteenProfileUiState(
    val canteenInfo: CanteenInfo = CanteenInfo(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isRegeneratingCode: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class CanteenProfileViewModel(
    private val repository: CanteenRepository = CanteenRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CanteenProfileUiState())
    val uiState: StateFlow<CanteenProfileUiState> = _uiState.asStateFlow()

    init {
        loadCanteenInfo()
    }

    fun loadCanteenInfo() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.getCanteenInfo()
                .onSuccess { info ->
                    SessionManager.canteenJoinCode = info.joinCode
                    _uiState.update { state ->
                        state.copy(
                            canteenInfo = info,
                            isLoading = false
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = throwable.localizedMessage ?: "Failed to load canteen details"
                        )
                    }
                }
        }
    }

    fun updateCanteenDetails(name: String, ownerName: String, location: String, photoUrl: String?) {
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Canteen name cannot be empty.") }
            return
        }
        if (ownerName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Owner name cannot be empty.") }
            return
        }
        if (location.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Location cannot be empty.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            repository.updateCanteenInfo(
                name = name.trim(),
                ownerName = ownerName.trim(),
                location = location.trim(),
                photoUrl = photoUrl?.trim().takeIf { !it.isNullOrBlank() }
            )
                .onSuccess { updatedInfo ->
                    SessionManager.canteenName = updatedInfo.name
                    SessionManager.ownerName = updatedInfo.ownerName
                    SessionManager.canteenLocation = updatedInfo.location
                    SessionManager.canteenPhotoUrl = updatedInfo.photoUrl
                    SessionManager.canteenJoinCode = updatedInfo.joinCode

                    _uiState.update { state ->
                        state.copy(
                            canteenInfo = updatedInfo,
                            isSaving = false,
                            successMessage = "Canteen details saved successfully!"
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isSaving = false,
                            errorMessage = throwable.localizedMessage ?: "Failed to save canteen details."
                        )
                    }
                }
        }
    }

    fun regenerateJoinCode() {
        _uiState.update { it.copy(isRegeneratingCode = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            repository.regenerateJoinCode()
                .onSuccess { apiCode ->
                    val codeToUse = apiCode.trim().uppercase()
                    if (!CanteenCodeUtils.isValidCode(codeToUse)) {
                        _uiState.update { state ->
                            state.copy(
                                isRegeneratingCode = false,
                                errorMessage = "Server returned an invalid canteen join code."
                            )
                        }
                        return@onSuccess
                    }
                    SessionManager.canteenJoinCode = codeToUse
                    _uiState.update { state ->
                        state.copy(
                            canteenInfo = state.canteenInfo.copy(joinCode = codeToUse),
                            isRegeneratingCode = false,
                            successMessage = "New 6-character canteen join code generated: $codeToUse"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            isRegeneratingCode = false,
                            errorMessage = error.localizedMessage ?: "Failed to regenerate canteen join code."
                        )
                    }
                }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
