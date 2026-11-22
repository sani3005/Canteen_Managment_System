package com.canteen.student.data.api

import com.canteen.student.data.model.ApiResponse
import com.canteen.student.data.model.AnalyticsSummaryData
import com.canteen.student.data.model.CanteenInfo
import com.canteen.student.data.model.CreateCanteenData
import com.canteen.student.data.model.CreateCanteenRequest
import com.canteen.student.data.model.CreateMenuItemRequest
import com.canteen.student.data.model.ItemCreatedResponse
import com.canteen.student.data.model.ItemDeletedResponse
import com.canteen.student.data.model.ItemUpdatedResponse
import com.canteen.student.data.model.JoinCodeData
import com.canteen.student.data.model.LiveOrdersData
import com.canteen.student.data.model.MenuDataResponse
import com.canteen.student.data.model.OrderStatusData
import com.canteen.student.data.model.OtpVerificationRequest
import com.canteen.student.data.model.OtpVerifyData
import com.canteen.student.data.model.OwnerLoginData
import com.canteen.student.data.model.OwnerLoginRequest
import com.canteen.student.data.model.OwnerRegisterData
import com.canteen.student.data.model.OwnerRegisterRequest
import com.canteen.student.data.model.PlaceOrderData
import com.canteen.student.data.model.PlaceOrderRequest
import com.canteen.student.data.model.RushHoursData
import com.canteen.student.data.model.StudentMenuData
import com.canteen.student.data.model.StudentLoginData
import com.canteen.student.data.model.StudentLoginRequest
import com.canteen.student.data.model.StudentOrdersData
import com.canteen.student.data.model.StudentRegisterData
import com.canteen.student.data.model.StudentRegisterRequest
import com.canteen.student.data.model.ToggleStockRequest
import com.canteen.student.data.model.ToggleStockResponse
import com.canteen.student.data.model.TopDishesData
import com.canteen.student.data.model.UpdateCanteenInfoRequest
import com.canteen.student.data.model.UpdateMenuItemRequest
import com.canteen.student.data.model.UpdateOrderStatusRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * Retrofit REST API interface for Canteen Backend endpoints.
 */
interface CanteenApiService {

    // --- PUBLIC ENDPOINTS ---

    @GET("health")
    suspend fun getHealth(): Response<Map<String, Any>>

    @POST("student/register")
    suspend fun registerStudent(@Body request: StudentRegisterRequest): Response<ApiResponse<StudentRegisterData>>

    @POST("student/login")
    suspend fun loginStudent(@Body request: StudentLoginRequest): Response<ApiResponse<StudentLoginData>>

    @POST("owner/register")
    suspend fun registerOwner(@Body request: OwnerRegisterRequest): Response<ApiResponse<OwnerRegisterData>>

    @POST("owner/login")
    suspend fun loginOwner(@Body request: OwnerLoginRequest): Response<ApiResponse<OwnerLoginData>>


    // --- STUDENT ENDPOINTS ---

    @GET("student/menu")
    suspend fun getStudentMenu(): Response<ApiResponse<StudentMenuData>>

    @POST("student/orders")
    suspend fun placeStudentOrder(@Body request: PlaceOrderRequest): Response<ApiResponse<PlaceOrderData>>

    @GET("student/orders")
    suspend fun getStudentOrders(): Response<ApiResponse<StudentOrdersData>>


    // --- OWNER CANTEEN ENDPOINTS ---

    @POST("owner/canteen")
    suspend fun createCanteen(@Body request: CreateCanteenRequest): Response<ApiResponse<CreateCanteenData>>

    @GET("owner/canteen")
    suspend fun getCanteenInfo(): Response<ApiResponse<CanteenInfo>>

    @PUT("owner/canteen")
    suspend fun updateCanteenInfo(@Body request: UpdateCanteenInfoRequest): Response<ApiResponse<CanteenInfo>>

    @POST("owner/canteen/join-code")
    suspend fun regenerateJoinCode(): Response<ApiResponse<JoinCodeData>>


    // --- OWNER MENU ENDPOINTS ---

    @GET("owner/menu")
    suspend fun getOwnerMenu(): Response<ApiResponse<MenuDataResponse>>

    @POST("owner/menu/item")
    suspend fun createMenuItem(@Body request: CreateMenuItemRequest): Response<ApiResponse<ItemCreatedResponse>>

    @PUT("owner/menu/item")
    suspend fun updateMenuItem(@Body request: UpdateMenuItemRequest): Response<ApiResponse<ItemUpdatedResponse>>

    @PATCH("owner/menu/item/toggle-stock")
    suspend fun toggleStock(@Body request: ToggleStockRequest): Response<ApiResponse<ToggleStockResponse>>

    @DELETE("owner/menu/item")
    suspend fun deleteMenuItem(@Query("id") itemId: Int): Response<ApiResponse<ItemDeletedResponse>>


    // --- OWNER ORDER ENDPOINTS ---

    @GET("owner/orders/live")
    suspend fun getLiveOrders(): Response<ApiResponse<LiveOrdersData>>

    @PATCH("owner/orders/status")
    suspend fun updateOrderStatus(@Body request: UpdateOrderStatusRequest): Response<ApiResponse<OrderStatusData>>

    @POST("owner/orders/verify-otp")
    suspend fun verifyOtp(@Body request: OtpVerificationRequest): Response<ApiResponse<OtpVerifyData>>


    // --- ANALYTICS ENDPOINTS ---

    @GET("owner/analytics/summary")
    suspend fun getAnalyticsSummary(): Response<ApiResponse<AnalyticsSummaryData>>

    @GET("owner/analytics/rush-hours")
    suspend fun getRushHours(): Response<ApiResponse<RushHoursData>>

    @GET("owner/analytics/top-dishes")
    suspend fun getTopDishes(): Response<ApiResponse<TopDishesData>>
}
