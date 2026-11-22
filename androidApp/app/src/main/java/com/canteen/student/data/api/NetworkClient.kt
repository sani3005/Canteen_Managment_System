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

    private const val DEFAULT_BASE_URL = "http://10.115.42.15:8080/api/"
    private var baseUrl: String = DEFAULT_BASE_URL
    private var useMockBackend: Boolean = false

    private val gson = Gson()

    fun setBaseUrl(url: String) {
        baseUrl = url
        rebuildRetrofit()
    }

    fun setUseMockBackend(enable: Boolean) {
        useMockBackend = enable
        rebuildRetrofit()
    }

    val apiService: CanteenApiService by lazy {
        getRetrofit().create(CanteenApiService::class.java)
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

        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            val token = SessionManager.activeToken
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }

        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
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
            val mediaType = "application/json".toMediaType()
            val responseJson = gson.toJson(ApiResponse<Any>(status = "success", message = "Mock response OK", data = null))

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
