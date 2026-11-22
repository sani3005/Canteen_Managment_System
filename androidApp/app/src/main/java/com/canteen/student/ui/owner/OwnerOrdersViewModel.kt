package com.canteen.student.ui.owner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canteen.student.data.model.Order
import com.canteen.student.data.model.OrderStatus
import com.canteen.student.data.repository.CanteenRepository
import com.canteen.student.data.repository.CanteenRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OwnerOrdersUiState(
    val orders: List<Order> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusFilter: OrderStatus? = null,
    val selectedOrderForOtp: Order? = null,
    val otpInput: String = "",
    val otpVerifying: Boolean = false,
    val otpErrorMessage: String? = null,
    val otpSuccessMessage: String? = null
)

class OwnerOrdersViewModel(
    private val repository: CanteenRepository = CanteenRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(OwnerOrdersUiState())
    val uiState: StateFlow<OwnerOrdersUiState> = _uiState.asStateFlow()

    init {
        fetchOrders()
    }

    fun fetchOrders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getPendingOrders()
                .onSuccess { orderList ->
                    _uiState.update {
                        it.copy(
                            orders = orderList,
                            isLoading = false
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.localizedMessage ?: "Failed to load orders"
                        )
                    }
                }
        }
    }

    fun setStatusFilter(filter: OrderStatus?) {
        _uiState.update { it.copy(statusFilter = filter) }
    }

    fun updateOrderStatus(orderId: String, newStatus: OrderStatus) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.updateOrderStatus(orderId, newStatus)
                .onSuccess {
                    fetchOrders()
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.localizedMessage ?: "Failed to update status"
                        )
                    }
                }
        }
    }

    fun openOtpDialog(order: Order) {
        _uiState.update {
            it.copy(
                selectedOrderForOtp = order,
                otpInput = "",
                otpErrorMessage = null,
                otpSuccessMessage = null
            )
        }
    }

    fun closeOtpDialog() {
        _uiState.update {
            it.copy(
                selectedOrderForOtp = null,
                otpInput = "",
                otpVerifying = false,
                otpErrorMessage = null,
                otpSuccessMessage = null
            )
        }
    }

    fun setOtpInput(input: String) {
        _uiState.update { it.copy(otpInput = input, otpErrorMessage = null) }
    }

    fun verifyOtp() {
        val currentOrder = _uiState.value.selectedOrderForOtp ?: return
        val otp = _uiState.value.otpInput.trim()

        if (otp.length < 4) {
            _uiState.update { it.copy(otpErrorMessage = "Enter full 4-digit OTP") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(otpVerifying = true, otpErrorMessage = null) }
            repository.verifyOtp(currentOrder.id, otp)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            otpVerifying = false,
                            otpSuccessMessage = response.message
                        )
                    }
                    fetchOrders()
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            otpVerifying = false,
                            otpErrorMessage = throwable.localizedMessage ?: "Invalid OTP code"
                        )
                    }
                }
        }
    }
}
