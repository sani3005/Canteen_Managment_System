package com.canteen.student.data.api

import com.canteen.student.data.model.ApiResponse
import com.canteen.student.data.model.AnalyticsSummaryData
import com.canteen.student.data.model.CanteenInfo
import com.canteen.student.data.model.CreateMenuItemRequest
import com.canteen.student.data.model.ItemCreatedResponse
import com.canteen.student.data.model.ItemDeletedResponse
import com.canteen.student.data.model.ItemUpdatedResponse
import com.canteen.student.data.model.JoinCodeData
import com.canteen.student.data.model.LiveOrdersData
import com.canteen.student.data.model.MenuDataResponse
import com.canteen.student.data.model.MenuItem
import com.canteen.student.data.model.Order
import com.canteen.student.data.model.OrderItem
import com.canteen.student.data.model.OrderStatus
import com.canteen.student.data.model.OrderStatusData
import com.canteen.student.data.model.OrderType
import com.canteen.student.data.model.OtpVerificationRequest
import com.canteen.student.data.model.OtpVerifyData
import com.canteen.student.data.model.PackagingBreakdown
import com.canteen.student.data.model.RushHour
import com.canteen.student.data.model.RushHoursData
import com.canteen.student.data.model.ToggleStockRequest
import com.canteen.student.data.model.ToggleStockResponse
import com.canteen.student.data.model.TopDishesData
import com.canteen.student.data.model.TopSellingItem
import com.canteen.student.data.model.UpdateMenuItemRequest
import com.canteen.student.data.model.UpdateOrderStatusRequest
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Provides Retrofit client configuration with Mock Backend interceptor fallback.
 */
object NetworkClient {

    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/api/"
    private var baseUrl: String = DEFAULT_BASE_URL
    private var useMockBackend: Boolean = true

    private val gson = Gson()

    // Mock storage for offline development
    private val mockMenuItems = mutableListOf(
        MenuItem(1, "Veg Burger", "Snacks", 60.0, "Delicious veg burger", true),
        MenuItem(2, "Paneer Tikka", "Snacks", 120.0, "Spicy paneer tikka", true),
        MenuItem(3, "Cold Coffee", "Beverages", 50.0, "Chilled coffee with ice cream", true),
        MenuItem(4, "Thali Meal", "Lunch", 150.0, "Complete meal thali", true),
        MenuItem(5, "Masala Dosa", "Breakfast", 70.0, "Crispy masala dosa", false)
    )

    private val mockOrders = mutableListOf(
        Order(
            orderId = 101,
            studentId = 1,
            studentName = "Rahul Sharma",
            studentPhone = "9876543210",
            orderType = OrderType.PARCEL,
            packagingLabel = "TAK-101",
            isPreorder = false,
            scheduledTime = null,
            status = OrderStatus.PENDING,
            totalAmount = 170.0,
            created_At = "10:30 AM",
            items = listOf(
                OrderItem("Veg Burger", 60.0, 2, 120.0, "1"),
                OrderItem("Cold Coffee", 50.0, 1, 50.0, "3")
            ),
            otpCode = "4829"
        ),
        Order(
            orderId = 102,
            studentId = 2,
            studentName = "Priya Patel",
            studentPhone = "9876543211",
            orderType = OrderType.EAT_IN,
            packagingLabel = "DINE-102",
            isPreorder = true,
            scheduledTime = "1:00 PM",
            status = OrderStatus.PREPARING,
            totalAmount = 150.0,
            created_At = "11:00 AM",
            items = listOf(
                OrderItem("Thali Meal", 150.0, 1, 150.0, "4")
            ),
            otpCode = "1934"
        ),
        Order(
            orderId = 103,
            studentId = 3,
            studentName = "Amit Kumar",
            studentPhone = "9876543212",
            orderType = OrderType.EAT_IN,
            packagingLabel = "DINE-103",
            isPreorder = false,
            scheduledTime = null,
            status = OrderStatus.READY,
            totalAmount = 120.0,
            created_At = "11:15 AM",
            items = listOf(
                OrderItem("Paneer Tikka", 120.0, 1, 120.0, "2")
            ),
            otpCode = "7361"
        )
    )

    fun setBaseUrl(url: String) {
        baseUrl = url
        rebuildRetrofit()
    }

    fun setUseMockBackend(enable: Boolean) {
        useMockBackend = enable
        rebuildRetrofit()
    }

    val apiService: CanteenApiService by lazy {
        createRetrofit().create(CanteenApiService::class.java)
    }

    private var retrofitInstance: Retrofit? = null

    private fun getRetrofit(): Retrofit {
        if (retrofitInstance == null) {
            retrofitInstance = createRetrofit()
        }
        return retrofitInstance!!
    }

    private fun rebuildRetrofit() {
        retrofitInstance = createRetrofit()
    }

    private fun createRetrofit(): Retrofit {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)

        if (useMockBackend) {
            clientBuilder.addInterceptor(MockBackendInterceptor())
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private class MockBackendInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val uri = request.url.toUri().toString()
            val method = request.method

            val mediaType = "application/json".toMediaType()

            val responseJson = when {
                uri.endsWith("owner/menu") && method == "GET" -> {
                    val data = MenuDataResponse(
                        count = mockMenuItems.size,
                        items = mockMenuItems.toList()
                    )
                    gson.toJson(ApiResponse(status = "success", message = "Menu retrieved", data = data))
                }

                uri.endsWith("owner/menu") && method == "POST" -> {
                    val newItem = mockMenuItems.lastOrNull()?.copy(
                        itemId = (mockMenuItems.maxOfOrNull { it.itemId } ?: 0) + 1,
                        name = "New Menu Item",
                        price = 99.0
                    ) ?: MenuItem(1, "New Item", "General", 99.0)
                    mockMenuItems.add(newItem)
                    gson.toJson(ApiResponse(status = "success", message = "Item created", data = ItemCreatedResponse(newItem.itemId)))
                }

                uri.endsWith("owner/menu/toggle-stock") -> {
                    gson.toJson(ApiResponse(status = "success", message = "Stock updated", data = ToggleStockResponse(1, true)))
                }

                uri.endsWith("owner/orders/live") -> {
                    val data = LiveOrdersData(
                        immediateOrders = mockOrders.filter { !it.isPreorder },
                        preOrders = mockOrders.filter { it.isPreorder },
                        totalImmediate = mockOrders.count { !it.isPreorder },
                        totalPreorders = mockOrders.count { it.isPreorder },
                        totalActive = mockOrders.size
                    )
                    gson.toJson(ApiResponse(status = "success", message = "Orders retrieved", data = data))
                }

                uri.contains("owner/orders/status") -> {
                    gson.toJson(ApiResponse(status = "success", message = "Status updated", data = OrderStatusData(101, "preparing")))
                }

                uri.endsWith("owner/orders/verify-otp") -> {
                    gson.toJson(ApiResponse(status = "success", message = "OTP verified", data = OtpVerifyData(101, "completed", 170.0)))
                }

                uri.endsWith("owner/analytics/summary") -> {
                    val data = AnalyticsSummaryData(
                        allTimeRevenue = 45200.0,
                        allTimeOrders = 320,
                        todayRevenue = 3450.0,
                        todayOrders = 28,
                        averageOrderValue = 141.25,
                        packagingBreakdown = PackagingBreakdown(dineInOrders = 18, parcelOrders = 10)
                    )
                    gson.toJson(ApiResponse(status = "success", message = "Analytics loaded", data = data))
                }

                uri.endsWith("owner/analytics/rush-hours") -> {
                    val data = RushHoursData(
                        peakRushHours = listOf(
                            RushHour(12, "12:00 PM - 1:00 PM", 45, 6200.0),
                            RushHour(13, "1:00 PM - 2:00 PM", 60, 8500.0),
                            RushHour(17, "5:00 PM - 6:00 PM", 35, 4200.0)
                        )
                    )
                    gson.toJson(ApiResponse(status = "success", message = "Rush hours loaded", data = data))
                }

                uri.endsWith("owner/analytics/top-dishes") -> {
                    val data = TopDishesData(
                        topSellingDishes = listOf(
                            TopSellingItem(1, 1, "Veg Burger", "Snacks", 140, 8400.0),
                            TopSellingItem(2, 4, "Thali Meal", "Lunch", 95, 14250.0),
                            TopSellingItem(3, 3, "Cold Coffee", "Beverages", 110, 5500.0)
                        )
                    )
                    gson.toJson(ApiResponse(status = "success", message = "Top dishes loaded", data = data))
                }

                else -> {
                    gson.toJson(ApiResponse<Any>(status = "success", message = "Mock response OK", data = null))
                }
            }

            return Response.Builder()
                .code(200)
                .message("OK")
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .body(responseJson.toResponseBody(mediaType))
                .build()
        }
    }
}
