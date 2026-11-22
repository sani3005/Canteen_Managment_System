package com.canteen.student.data.model

import com.google.gson.annotations.SerializedName

/**
 * Standard API Response Envelope used by the backend.
 */
data class ApiResponse<T>(
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null
)

// --- PUBLIC & STUDENT DTOs ---

data class StudentRegisterRequest(
    @SerializedName("full_name") val fullName: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("join_code") val joinCode: String,
    @SerializedName("phone") val phone: String? = null
)

data class StudentRegisterData(
    @SerializedName("student_id") val studentId: Int,
    @SerializedName("canteen_id") val canteenId: Int,
    @SerializedName("token") val token: String,
    @SerializedName("token_type") val tokenType: String
)

data class StudentLoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class StudentLoginData(
    @SerializedName("student_id") val studentId: Int,
    @SerializedName("canteen_id") val canteenId: Int,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("token") val token: String,
    @SerializedName("token_type") val tokenType: String
)

data class StudentMenuData(
    @SerializedName("canteen_id") val canteenId: Int,
    @SerializedName("count") val count: Int,
    @SerializedName("items") val items: List<MenuItem>
)

data class OrderItemInput(
    @SerializedName("item_id") val itemId: Int,
    @SerializedName("quantity") val quantity: Int
)

data class PlaceOrderRequest(
    @SerializedName("order_type") val orderType: String = "dine_in",
    @SerializedName("items") val items: List<OrderItemInput>
)

data class PlaceOrderData(
    @SerializedName("order_id") val orderId: Int,
    @SerializedName("status") val status: String,
    @SerializedName("order_type") val orderType: String,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("message") val message: String? = null
)

data class StudentOrdersData(
    @SerializedName("count") val count: Int,
    @SerializedName("orders") val orders: List<StudentOrderResponse>
)

data class StudentOrderResponse(
    @SerializedName("order_id") val orderId: Int,
    @SerializedName("order_type") val orderType: String,
    @SerializedName("status") val status: String,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("pickup_otp") val pickupOtp: String? = null
)

// --- OWNER CANTEEN DTOs ---

object CanteenCodeUtils {
    /**
     * Generates a random 6-character uppercase alphanumeric code (e.g., "CN7X9K", "A3B8Z1").
     */
    fun generate6CharAlphanumericCode(): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6)
            .map { allowedChars.random() }
            .joinToString("")
    }

    /**
     * Validates if a string is a 6-character alphanumeric code.
     */
    fun isValidCode(code: String): Boolean {
        return code.trim().length == 6 && code.trim().all { it.isLetterOrDigit() }
    }
}

data class CreateCanteenRequest(
    @SerializedName("name") val name: String,
    @SerializedName("owner_name") val ownerName: String,
    @SerializedName("location") val location: String,
    @SerializedName("photo_url") val photoUrl: String? = null
)

data class CreateCanteenData(
    @SerializedName("canteen_id") val canteenId: Int,
    @SerializedName("join_code") val joinCode: String
)

data class OwnerRegisterRequest(
    @SerializedName("full_name") val fullName: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("canteen_name") val canteenName: String,
    @SerializedName("location") val location: String,
    @SerializedName("photo_url") val photoUrl: String? = null
)

data class OwnerRegisterData(
    @SerializedName("canteen_id") val canteenId: Int = 0,
    @SerializedName("join_code") val joinCode: String = "",
    @SerializedName("token") val token: String = "",
    @SerializedName("token_type") val tokenType: String = "Bearer"
)

data class OwnerLoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class OwnerLoginData(
    @SerializedName("canteen_id") val canteenId: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("owner_name") val ownerName: String = "",
    @SerializedName("location") val location: String = "",
    @SerializedName("photo_url") val photoUrl: String? = null,
    @SerializedName("join_code") val joinCode: String = "",
    @SerializedName("token") val token: String = "",
    @SerializedName("token_type") val tokenType: String = "Bearer"
)

data class CanteenInfo(
    @SerializedName("canteen_id") val canteenId: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("owner_name") val ownerName: String = "",
    @SerializedName("location") val location: String = "",
    @SerializedName("photo_url") val photoUrl: String? = null,
    @SerializedName("join_code") val joinCode: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)

data class UpdateCanteenInfoRequest(
    @SerializedName("name") val name: String,
    @SerializedName("owner_name") val ownerName: String,
    @SerializedName("location") val location: String,
    @SerializedName("photo_url") val photoUrl: String? = null
)

data class JoinCodeData(
    @SerializedName("join_code") val joinCode: String,
    @SerializedName("canteen_id") val canteenId: Int
)

// --- OWNER MENU DTOs ---

data class MenuDataResponse(
    @SerializedName("count") val count: Int = 0,
    @SerializedName("items") val items: List<MenuItem> = emptyList()
)

/**
 * Represents a food item in the canteen menu.
 */
data class MenuItem(
    @SerializedName("item_id") val itemId: Int = 0,
    @SerializedName("name") val name: String,
    @SerializedName("category") val category: String = "General",
    @SerializedName("price") val price: Double,
    @SerializedName("description") val description: String = "",
    @SerializedName("is_available") val isAvailable: Boolean = true,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null
) {
    val id: String get() = itemId.toString()
}

data class CreateMenuItemRequest(
    @SerializedName("name") val name: String,
    @SerializedName("price") val price: Double,
    @SerializedName("description") val description: String = "",
    @SerializedName("category") val category: String = "General",
    @SerializedName("is_available") val isAvailable: Boolean = true
)

data class ItemCreatedResponse(
    @SerializedName("item_id") val itemId: Int
)

data class UpdateMenuItemRequest(
    @SerializedName("item_id") val itemId: Int,
    @SerializedName("name") val name: String? = null,
    @SerializedName("price") val price: Double? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("category") val category: String? = null
)

data class ItemUpdatedResponse(
    @SerializedName("item_id") val itemId: Int
)

data class ToggleStockRequest(
    @SerializedName("item_id") val itemId: Int,
    @SerializedName("is_available") val isAvailable: Boolean? = null
)

data class ToggleStockResponse(
    @SerializedName("item_id") val itemId: Int,
    @SerializedName("is_available") val isAvailable: Boolean
)

data class ItemDeletedResponse(
    @SerializedName("deleted_item_id") val deletedItemId: Int
)

// --- OWNER ORDER DTOs ---

/**
 * Specifies if order is parcel (takeaway) or to eat inside the canteen.
 */
enum class OrderType {
    @SerializedName("dine_in")
    EAT_IN,

    @SerializedName("parcel")
    PARCEL
}

/**
 * Lifecycle status of an order.
 */
enum class OrderStatus {
    @SerializedName("pending")
    PENDING,

    @SerializedName("preparing")
    PREPARING,

    @SerializedName("ready")
    READY,

    @SerializedName("completed")
    COMPLETED,

    @SerializedName("cancelled")
    CANCELLED
}

/**
 * Itemized breakdown inside an order.
 */
data class OrderItem(
    @SerializedName("name") val itemName: String = "",
    @SerializedName("unit_price") val price: Double = 0.0,
    @SerializedName("quantity") val quantity: Int = 0,
    @SerializedName("subtotal") val subtotal: Double = 0.0,
    @SerializedName("itemId") val itemId: String = "",
    @SerializedName("notes") val notes: String? = null
)

/**
 * Order details retrieved by canteen owner.
 */
data class Order(
    @SerializedName("order_id") val orderId: Int = 0,
    @SerializedName("student_id") val studentId: Int = 0,
    @SerializedName("student_name") val studentName: String = "",
    @SerializedName("student_phone") val studentPhone: String = "",
    @SerializedName("order_type") val orderType: OrderType = OrderType.EAT_IN,
    @SerializedName("packaging_label") val packagingLabel: String = "",
    @SerializedName("is_preorder") val isPreorder: Boolean = false,
    @SerializedName("scheduled_time") val scheduledTime: String? = null,
    @SerializedName("status") val status: OrderStatus = OrderStatus.PENDING,
    @SerializedName("total_amount") val totalAmount: Double = 0.0,
    @SerializedName("created_at") val created_At: String = "",
    @SerializedName("items") val items: List<OrderItem> = emptyList(),
    @SerializedName("pickup_otp") val otpCode: String = ""
) {
    val id: String get() = orderId.toString()
    val orderTime: String get() = created_At
    val preOrderTime: String? get() = scheduledTime
}

data class LiveOrdersData(
    @SerializedName("immediate_orders") val immediateOrders: List<Order> = emptyList(),
    @SerializedName("pre_orders") val preOrders: List<Order> = emptyList(),
    @SerializedName("total_immediate") val totalImmediate: Int = 0,
    @SerializedName("total_preorders") val totalPreorders: Int = 0,
    @SerializedName("total_active") val totalActive: Int = 0
)

data class UpdateOrderStatusRequest(
    @SerializedName("order_id") val orderId: Int,
    @SerializedName("status") val status: String
)

data class OrderStatusData(
    @SerializedName("order_id") val orderId: Int,
    @SerializedName("new_status") val newStatus: String
)

data class OtpVerificationRequest(
    @SerializedName("order_id") val orderId: Int,
    @SerializedName("otp") val otp: String
)

data class OtpVerifyData(
    @SerializedName("order_id") val orderId: Int,
    @SerializedName("status") val status: String,
    @SerializedName("total_amount") val totalAmount: Double
)

data class OtpVerificationResponse(
    val success: Boolean,
    val message: String,
    val order: Order? = null
)

// --- ANALYTICS DTOs ---

data class PackagingBreakdown(
    @SerializedName("dine_in_orders") val dineInOrders: Int = 0,
    @SerializedName("parcel_orders") val parcelOrders: Int = 0
)

data class AnalyticsSummaryData(
    @SerializedName("all_time_revenue") val allTimeRevenue: Double = 0.0,
    @SerializedName("all_time_orders") val allTimeOrders: Int = 0,
    @SerializedName("today_revenue") val todayRevenue: Double = 0.0,
    @SerializedName("today_orders") val todayOrders: Int = 0,
    @SerializedName("average_order_value") val averageOrderValue: Double = 0.0,
    @SerializedName("packaging_breakdown") val packagingBreakdown: PackagingBreakdown = PackagingBreakdown()
)

data class RushHour(
    @SerializedName("hour") val hour: Int = 0,
    @SerializedName("time_window") val timeWindow: String = "",
    @SerializedName("completed_orders") val completedOrders: Int = 0,
    @SerializedName("revenue") val revenue: Double = 0.0
)

data class RushHoursData(
    @SerializedName("peak_rush_hours") val peakRushHours: List<RushHour> = emptyList()
)

data class TopSellingItem(
    @SerializedName("rank") val rank: Int = 0,
    @SerializedName("item_id") val item_Id: Int = 0,
    @SerializedName("name") val itemName: String = "",
    @SerializedName("category") val category: String = "",
    @SerializedName("total_units_sold") val quantitySold: Int = 0,
    @SerializedName("total_revenue") val totalRevenue: Double = 0.0
) {
    val itemId: String get() = item_Id.toString()
}

data class TopDishesData(
    @SerializedName("top_selling_dishes") val topSellingDishes: List<TopSellingItem> = emptyList()
)

data class PeakDay(
    @SerializedName("day") val day: String = "",
    @SerializedName("day_full") val dayFull: String = "",
    @SerializedName("completed_orders") val completedOrders: Int = 0,
    @SerializedName("revenue") val revenue: Double = 0.0
)

data class PeakDaysData(
    @SerializedName("peak_days") val peakDays: List<PeakDay> = emptyList()
)

data class TopDishesByDayData(
    @SerializedName("top_dishes_by_day") val topDishesByDay: Map<String, List<TopSellingItem>> = emptyMap()
)

data class AnalyticsSummary(
    val totalRevenue: Double = 0.0,
    val totalOrders: Int = 0,
    val completedOrders: Int = 0,
    val cancelledOrders: Int = 0,
    val parcelCount: Int = 0,
    val eatInCount: Int = 0,
    val topSellingItems: List<TopSellingItem> = emptyList(),
    val rushHours: List<RushHour> = emptyList(),
    val peakDays: List<PeakDay> = emptyList(),
    val topDishesByDay: Map<String, List<TopSellingItem>> = emptyMap(),
    val recentOrders: List<Order> = emptyList()
)
