package com.canteen.student.data.repository

import com.canteen.student.data.api.CanteenApiService
import com.canteen.student.data.api.NetworkClient
import com.canteen.student.data.api.SessionManager
import com.canteen.student.data.model.ApiResponse
import com.canteen.student.data.model.AnalyticsSummary
import com.canteen.student.data.model.CanteenInfo
import com.canteen.student.data.model.CreateCanteenData
import com.canteen.student.data.model.CreateCanteenRequest
import com.canteen.student.data.model.CreateMenuItemRequest
import com.canteen.student.data.model.MenuItem
import com.canteen.student.data.model.Order
import com.canteen.student.data.model.OrderStatus
import com.canteen.student.data.model.OtpVerificationRequest
import com.canteen.student.data.model.OtpVerificationResponse
import com.canteen.student.data.model.OwnerLoginData
import com.canteen.student.data.model.OwnerLoginRequest
import com.canteen.student.data.model.OwnerRegisterData
import com.canteen.student.data.model.OwnerRegisterRequest
import com.canteen.student.data.model.ToggleStockRequest
import com.canteen.student.data.model.UpdateCanteenInfoRequest
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

    suspend fun createCanteen(name: String, ownerName: String, location: String, photoUrl: String?): Result<CreateCanteenData>
    suspend fun getCanteenInfo(): Result<CanteenInfo>
    suspend fun updateCanteenInfo(name: String, ownerName: String, location: String, photoUrl: String?): Result<CanteenInfo>
    suspend fun regenerateJoinCode(): Result<String>

    suspend fun registerOwner(
        fullName: String,
        email: String,
        password: String,
        canteenName: String,
        location: String,
        photoUrl: String? = null
    ): Result<OwnerRegisterData>

    suspend fun loginOwner(
        email: String,
        password: String
    ): Result<OwnerLoginData>
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
                throw Exception("Response data is empty")
            }
            throw Exception("Response body is empty")
        } else {
            val errorBodyString = response.errorBody()?.string()
            if (!errorBodyString.isNullOrEmpty()) {
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
            name = "",
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

        val peakDaysResp = apiService.getPeakDays()
        val peakDaysData = handleApiResponse(peakDaysResp)

        val topDishesByDayResp = apiService.getTopDishesByDay()
        val topDishesByDayData = handleApiResponse(topDishesByDayResp)

        AnalyticsSummary(
            totalRevenue = summaryData.todayRevenue,
            totalOrders = summaryData.todayOrders,
            completedOrders = summaryData.todayOrders,
            cancelledOrders = 0,
            parcelCount = summaryData.packagingBreakdown.parcelOrders,
            eatInCount = summaryData.packagingBreakdown.dineInOrders,
            topSellingItems = topData.topSellingDishes,
            rushHours = rushData.peakRushHours,
            peakDays = peakDaysData.peakDays,
            topDishesByDay = topDishesByDayData.topDishesByDay,
            recentOrders = emptyList()
        )
    }

    override suspend fun createCanteen(
        name: String,
        ownerName: String,
        location: String,
        photoUrl: String?
    ): Result<CreateCanteenData> = runCatching {
        val req = CreateCanteenRequest(
            name = name,
            ownerName = ownerName,
            location = location,
            photoUrl = photoUrl
        )
        val response = apiService.createCanteen(req)
        val data = handleApiResponse(response)
        SessionManager.canteenId = data.canteenId
        SessionManager.canteenJoinCode = data.joinCode
        SessionManager.canteenName = name
        SessionManager.ownerName = ownerName
        SessionManager.canteenLocation = location
        SessionManager.canteenPhotoUrl = photoUrl
        data
    }

    override suspend fun getCanteenInfo(): Result<CanteenInfo> = runCatching {
        val response = apiService.getCanteenInfo()
        val info = handleApiResponse(response)
        SessionManager.canteenId = info.canteenId
        SessionManager.canteenJoinCode = info.joinCode
        SessionManager.canteenName = info.name
        SessionManager.ownerName = info.ownerName
        SessionManager.canteenLocation = info.location
        SessionManager.canteenPhotoUrl = info.photoUrl
        info
    }

    override suspend fun updateCanteenInfo(
        name: String,
        ownerName: String,
        location: String,
        photoUrl: String?
    ): Result<CanteenInfo> = runCatching {
        val req = UpdateCanteenInfoRequest(
            name = name,
            ownerName = ownerName,
            location = location,
            photoUrl = photoUrl
        )
        val response = apiService.updateCanteenInfo(req)
        val updated = handleApiResponse(response)
        SessionManager.canteenName = updated.name
        SessionManager.ownerName = updated.ownerName
        SessionManager.canteenLocation = updated.location
        SessionManager.canteenPhotoUrl = updated.photoUrl
        SessionManager.canteenJoinCode = updated.joinCode
        updated
    }

    override suspend fun regenerateJoinCode(): Result<String> = runCatching {
        val response = apiService.regenerateJoinCode()
        val data = handleApiResponse(response)
        SessionManager.canteenJoinCode = data.joinCode
        data.joinCode
    }

    override suspend fun registerOwner(
        fullName: String,
        email: String,
        password: String,
        canteenName: String,
        location: String,
        photoUrl: String?
    ): Result<OwnerRegisterData> = runCatching {
        val req = OwnerRegisterRequest(
            fullName = fullName,
            email = email,
            password = password,
            canteenName = canteenName,
            location = location,
            photoUrl = photoUrl
        )
        val response = apiService.registerOwner(req)
        handleApiResponse(response)
    }

    override suspend fun loginOwner(
        email: String,
        password: String
    ): Result<OwnerLoginData> = runCatching {
        val req = OwnerLoginRequest(
            email = email,
            password = password
        )
        val response = apiService.loginOwner(req)
        handleApiResponse(response)
    }
}
