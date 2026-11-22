package com.canteen.student.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canteen.student.data.model.DateTimeUtils
import com.canteen.student.data.model.MenuItem
import com.canteen.student.data.model.OrderItemInput
import com.canteen.student.data.model.PlaceOrderData
import com.canteen.student.data.model.StudentOrderResponse
import com.canteen.student.data.repository.StudentRepository
import com.canteen.student.data.repository.StudentRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentUiState(
    val menuItems: List<MenuItem> = emptyList(),
    val categories: List<String> = listOf("All", "Breakfast", "Lunch", "Snacks", "Beverages"),
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val cart: Map<Int, Int> = emptyMap(),
    val orders: List<StudentOrderResponse> = emptyList(),
    val isLoadingMenu: Boolean = false,
    val isLoadingOrders: Boolean = false,
    val isPlacingOrder: Boolean = false,
    val isPreorder: Boolean = false,
    val scheduledTime: String? = null,
    val errorMessage: String? = null,
    val orderSuccessMessage: String? = null,
    val placedOrderData: PlaceOrderData? = null
)

class StudentViewModel(
    private val repository: StudentRepository = StudentRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudentUiState())
    val uiState: StateFlow<StudentUiState> = _uiState.asStateFlow()

    init {
        fetchMenu()
        fetchOrders()
    }

    fun fetchMenu() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMenu = true, errorMessage = null) }
            repository.getMenu()
                .onSuccess { items ->
                    _uiState.update {
                        it.copy(
                            menuItems = items,
                            isLoadingMenu = false
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoadingMenu = false,
                            errorMessage = throwable.localizedMessage ?: "Failed to load canteen menu"
                        )
                    }
                }
        }
    }

    fun fetchOrders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOrders = true) }
            repository.getOrders()
                .onSuccess { orderList ->
                    _uiState.update {
                        it.copy(
                            orders = orderList,
                            isLoadingOrders = false
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoadingOrders = false,
                            errorMessage = throwable.localizedMessage ?: "Failed to fetch student orders"
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

    fun setIsPreorder(isPreorder: Boolean) {
        _uiState.update { it.copy(isPreorder = isPreorder, scheduledTime = if (!isPreorder) null else it.scheduledTime) }
    }

    fun setScheduledTime(scheduledTime: String?) {
        _uiState.update { it.copy(scheduledTime = scheduledTime) }
    }

    fun addToCart(itemId: Int) {
        _uiState.update { state ->
            val currentQty = state.cart[itemId] ?: 0
            val updatedCart = state.cart.toMutableMap()
            updatedCart[itemId] = currentQty + 1
            state.copy(cart = updatedCart)
        }
    }

    fun removeFromCart(itemId: Int) {
        _uiState.update { state ->
            val currentQty = state.cart[itemId] ?: 0
            val updatedCart = state.cart.toMutableMap()
            if (currentQty <= 1) {
                updatedCart.remove(itemId)
            } else {
                updatedCart[itemId] = currentQty - 1
            }
            state.copy(cart = updatedCart)
        }
    }

    fun clearCart() {
        _uiState.update { it.copy(cart = emptyMap()) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissSuccessMessage() {
        _uiState.update { it.copy(orderSuccessMessage = null, placedOrderData = null) }
    }

    fun placeOrder(orderType: String) {
        val currentState = _uiState.value
        val currentCart = currentState.cart
        if (currentCart.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Your cart is empty. Please add items to place an order.") }
            return
        }

        if (currentState.isPreorder && currentState.scheduledTime.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Please select a pickup time for your pre-order.") }
            return
        }

        val orderItems = currentCart.map { (itemId, qty) ->
            OrderItemInput(itemId = itemId, quantity = qty)
        }

        val apiOrderType = if (orderType.equals("Parcel", ignoreCase = true)) "parcel" else "dine_in"

        viewModelScope.launch {
            _uiState.update { it.copy(isPlacingOrder = true, errorMessage = null, orderSuccessMessage = null) }
            repository.placeOrder(
                orderType = apiOrderType,
                items = orderItems,
                isPreorder = currentState.isPreorder,
                scheduledTime = if (currentState.isPreorder) currentState.scheduledTime else null
            )
                .onSuccess { placeOrderData ->
                    val rawTime = placeOrderData.scheduledTime ?: currentState.scheduledTime
                    val displayTime = DateTimeUtils.formatForDisplay(rawTime)
                    val preOrderInfoStr = if (placeOrderData.isPreorder || currentState.isPreorder) {
                        if (displayTime.isNotBlank()) " (Pre-order for $displayTime)" else " (Pre-order)"
                    } else ""

                    _uiState.update {
                        it.copy(
                            cart = emptyMap(),
                            isPlacingOrder = false,
                            placedOrderData = placeOrderData,
                            isPreorder = false,
                            scheduledTime = null,
                            orderSuccessMessage = "Order #${placeOrderData.orderId}$preOrderInfoStr placed successfully! Total: ₹${placeOrderData.totalAmount}"
                        )
                    }
                    fetchOrders()
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isPlacingOrder = false,
                            errorMessage = throwable.localizedMessage ?: "Failed to place order"
                        )
                    }
                }
        }
    }
}
