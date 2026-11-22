package com.canteen.student.data.api

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Provides Retrofit client configuration for backend communication.
 */
object NetworkClient {

    private const val DEFAULT_BASE_URL = "http://10.115.42.15:8080/api/"    //URL for connecting with phone
    private var baseUrl: String = DEFAULT_BASE_URL

    private val gson = Gson()

    fun setBaseUrl(url: String) {
        baseUrl = if (!url.endsWith("/")) "$url/" else url
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
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
}
