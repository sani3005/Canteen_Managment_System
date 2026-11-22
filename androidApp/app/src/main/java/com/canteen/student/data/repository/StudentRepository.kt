package com.canteen.student.data.repository

import com.canteen.student.data.api.CanteenApiService
import com.canteen.student.data.api.NetworkClient
import com.canteen.student.data.model.ApiResponse
import com.canteen.student.data.model.MenuItem
import com.canteen.student.data.model.OrderItemInput
import com.canteen.student.data.model.PlaceOrderData
import com.canteen.student.data.model.PlaceOrderRequest
import com.canteen.student.data.model.StudentOrderResponse
import com.canteen.student.data.model.StudentRegisterData
import com.canteen.student.data.model.StudentRegisterRequest
import com.google.gson.Gson
import retrofit2.Response

/**
 * Repository interface defining operations for Student functionalities.
 */
interface StudentRepository {
    suspend fun registerStudent(
        fullName: String,
        email: String,
        joinCode: String,
        phone: String? = null,
        studentNumber: String? = null
    ): Result<StudentRegisterData>

    suspend fun getMenu(): Result<List<MenuItem>>
    suspend fun placeOrder(orderType: String, items: List<OrderItemInput>): Result<PlaceOrderData>
    suspend fun getOrders(): Result<List<StudentOrderResponse>>
}

/**
 * Implementation of StudentRepository calling CanteenApiService.
 */
class StudentRepositoryImpl(
    private val apiService: CanteenApiService = NetworkClient.apiService
) : StudentRepository {

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
            throw Exception("Server returned status code ${response.code()}")
        }
    }

    override suspend fun registerStudent(
        fullName: String,
        email: String,
        joinCode: String,
        phone: String?,
        studentNumber: String?
    ): Result<StudentRegisterData> = runCatching {
        val req = StudentRegisterRequest(
            fullName = fullName.trim(),
            email = email.trim(),
            joinCode = joinCode.trim(),
            phone = phone?.trim().takeIf { !it.isNullOrEmpty() },
            studentNumber = studentNumber?.trim().takeIf { !it.isNullOrEmpty() }
        )
        val response = apiService.registerStudent(req)
        handleApiResponse(response)
    }

    override suspend fun getMenu(): Result<List<MenuItem>> = runCatching {
        val response = apiService.getStudentMenu()
        val data = handleApiResponse(response)
        data.items
    }

    override suspend fun placeOrder(
        orderType: String,
        items: List<OrderItemInput>
    ): Result<PlaceOrderData> = runCatching {
        val req = PlaceOrderRequest(orderType = orderType, items = items)
        val response = apiService.placeStudentOrder(req)
        handleApiResponse(response)
    }

    override suspend fun getOrders(): Result<List<StudentOrderResponse>> = runCatching {
        val response = apiService.getStudentOrders()
        val data = handleApiResponse(response)
        data.orders
    }
}
