package com.example.pinningapp.network

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/**
 * Note: For testing purposes, we're using https://jsonplaceholder.typicode.com
 * In a real application, you would use your own API with the self-signed certificate
 */
interface ApiService {
    @GET("todos/1")
    fun getTodo(): Call<Todo>

    companion object {
        private const val BASE_URL = "https://jsonplaceholder.typicode.com/"

        fun create(context: Context): ApiService {
            // Add logging interceptor
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            // Choose which implementation to use
            
            // Option 1: Standard OkHttpClient for public API endpoints
            val standardClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
                
            // Option 2: Certificate pinning with dynamic hash extraction
            val publicApiPinner = PublicApiPinner()
            val certificatePinningClient = publicApiPinner.createDynamicVerificationClient()
                .newBuilder()
                .addInterceptor(loggingInterceptor)
                .build()
            
            // Use standard client for public APIs
            val okHttpClient = standardClient

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(ApiService::class.java)
        }
    }
}

data class Todo(
    val id: Int,
    val userId: Int,
    val title: String,
    val completed: Boolean
)