package com.canteen.student.data.repository

import com.canteen.student.data.api.CanteenApiService
import com.canteen.student.data.api.NetworkClient
import com.canteen.student.data.model.ApiResponse
import com.canteen.student.data.model.AnalyticsSummary
import com.canteen.student.data.model.CreateMenuItemRequest
import com.canteen.student.data.model.MenuItem
import com.canteen.student.data.model.Order
import com.canteen.student.data.model.OrderStatus
import com.canteen.student.data.model.OtpVerificationRequest
import com.canteen.student.data.model.OtpVerificationResponse
import com.canteen.student.data.model.ToggleStockRequest
import com.canteen.student.data.model.UpdateMenuItemRequest
import com.canteen.student.data.model.UpdateOrderStatusRequest
import com.google.gson.Gson
import retrofit2.Response

/**
 * Repository interface defining operations for Canteen Owner functionalities.
 */
interface CanteenRepository {
    suspend fun getMenu(): Result<List<MenuItem>>
    suspend fun addMenuItem(item: MenuItem): Result<MenuItem>
    suspend fun updateMenuItem(item: MenuItem): Result<MenuItem>
    suspend fun toggleAvailability(id: String, isAvailable: Boolean): Result<MenuItem>
    suspend fun deleteMenuItem(id: String): Result<Unit>
    suspend fun getPendingOrders(): Result<List<Order>>
    suspend fun updateOrderStatus(id: String, status: OrderStatus): Result<Order>
    suspend fun verifyOtp(id: String, otp: String): Result<OtpVerificationResponse>
    suspend fun getAnalytics(): Result<AnalyticsSummary>
}

/**
 * Concrete implementation using Retrofit CanteenApiService.
 */
class CanteenRepositoryImpl(
    private val apiService: CanteenApiService = NetworkClient.apiService
) : CanteenRepository {

    private val gson = Gson()

    private inline fun <reified T> handleApiResponse(response: Response<ApiResponse<T>>): T {
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                if (body.status == "error") {
                    throw Exception(body.message ?: "Request failed")
                }
                if (body.data != null) {
                    return body.data
                }
                if (T::class.java == Unit::class.java) {
                    @Suppress("UNCHECKED_CAST")
                    return Unit as T
                }
                throw Exception("Response data is null")
            }
            throw Exception("Response body is null")
        } else {
            val errorBodyString = response.errorBody()?.string()
            if (!errorBodyString.isNull_or_Empty()) {
                try {
                    val apiErr = gson.fromJson(errorBodyString, ApiResponse::class.java)
                    if (!apiErr?.message.isNullOrEmpty()) {
                        throw Exception(apiErr.message)
                    }
                } catch (e: Exception) {
                    if (!e.message.isNullOrEmpty() && e.message != errorBodyString) {
                        throw e
                    }
                }
                throw Exception(errorBodyString)
            }
            throw Exception("Server returned HTTP error code ${response.code()}")
        }
    }

    private fun String?.isNull_or_Empty(): Boolean = this == null || this.isEmpty()

    override suspend fun getMenu(): Result<List<MenuItem>> = runCatching {
        val response = apiService.getOwnerMenu()
        val menuData = handleApiResponse(response)
        menuData.items
    }

    override suspend fun addMenuItem(item: MenuItem): Result<MenuItem> = runCatching {
        val req = CreateMenuItemRequest(
            name = item.name,
            price = item.price,
            description = item.description,
            category = item.category,
            isAvailable = item.isAvailable
        )
        val response = apiService.createMenuItem(req)
        val createdData = handleApiResponse(response)
        item.copy(itemId = createdData.itemId)
    }

    override suspend fun updateMenuItem(item: MenuItem): Result<MenuItem> = runCatching {
        val req = UpdateMenuItemRequest(
            itemId = item.itemId,
            name = item.name,
            price = item.price,
            description = item.description,
            category = item.category
        )
        val response = apiService.updateMenuItem(req)
        handleApiResponse(response)
        item
    }

    override suspend fun toggleAvailability(id: String, isAvailable: Boolean): Result<MenuItem> = runCatching {
        val itemIdInt = id.toIntOrNull() ?: throw IllegalArgumentException("Invalid Item ID format: $id")
        val req = ToggleStockRequest(itemId = itemIdInt, isAvailable = isAvailable)
        val response = apiService.toggleStock(req)
        val toggleData = handleApiResponse(response)
        MenuItem(
            itemId = toggleData.itemId,
            name = "Item #$id",
            price = 0.0,
            isAvailable = toggleData.isAvailable
        )
    }

    override suspend fun deleteMenuItem(id: String): Result<Unit> = runCatching {
        val itemIdInt = id.toIntOrNull() ?: throw IllegalArgumentException("Invalid Item ID format: $id")
        val response = apiService.deleteMenuItem(itemIdInt)
        handleApiResponse(response)
        Unit
    }

    override suspend fun getPendingOrders(): Result<List<Order>> = runCatching {
        val response = apiService.getLiveOrders()
        val liveData = handleApiResponse(response)
        val allOrders = mutableListOf<Order>()
        allOrders.addAll(liveData.immediateOrders)
        allOrders.addAll(liveData.preOrders)
        allOrders
    }

    override suspend fun updateOrderStatus(id: String, status: OrderStatus): Result<Order> = runCatching {
        val orderIdInt = id.toIntOrNull() ?: throw IllegalArgumentException("Invalid Order ID format: $id")
        val statusString = when (status) {
            OrderStatus.PENDING -> "pending"
            OrderStatus.PREPARING -> "preparing"
            OrderStatus.READY -> "ready"
            OrderStatus.COMPLETED -> "completed"
            OrderStatus.CANCELLED -> "cancelled"
        }
        val req = UpdateOrderStatusRequest(orderId = orderIdInt, status = statusString)
        val response = apiService.updateOrderStatus(req)
        val resultData = handleApiResponse(response)
        Order(orderId = resultData.orderId, status = status)
    }

    override suspend fun verifyOtp(id: String, otp: String): Result<OtpVerificationResponse> = runCatching {
        val orderIdInt = id.toIntOrNull() ?: throw IllegalArgumentException("Invalid Order ID format: $id")
        val req = OtpVerificationRequest(orderId = orderIdInt, otp = otp)
        val response = apiService.verifyOtp(req)
        val verifyData = handleApiResponse(response)
        OtpVerificationResponse(
            success = true,
            message = "Order verified successfully",
            order = Order(
                orderId = verifyData.orderId,
                status = OrderStatus.COMPLETED,
                totalAmount = verifyData.totalAmount
            )
        )
    }

    override suspend fun getAnalytics(): Result<AnalyticsSummary> = runCatching {
        val summaryResp = apiService.getAnalyticsSummary()
        val summaryData = handleApiResponse(summaryResp)

        val rushResp = apiService.getRushHours()
        val rushData = handleApiResponse(rushResp)

        val topResp = apiService.getTopDishes()
        val topData = handleApiResponse(topResp)

        AnalyticsSummary(
            totalRevenue = summaryData.todayRevenue,
            totalOrders = summaryData.todayOrders,
            completedOrders = summaryData.todayOrders,
            cancelledOrders = 0,
            parcelCount = summaryData.packagingBreakdown.parcelOrders,
            eatInCount = summaryData.packagingBreakdown.dineInOrders,
            topSellingItems = topData.topSellingDishes,
            rushHours = rushData.peakRushHours,
            recentOrders = emptyList()
        )
    }
}
