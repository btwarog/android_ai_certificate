package com.example.pinningapp.network

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/**
 * This interface is for testing with a local server.
 * Use the provided test scripts to set up a local server with the self-signed certificate.
 */
interface LocalTestApi {
    @GET("todos/1")
    fun getTodo(): Call<Todo>

    companion object {
        // For emulator, use 10.0.2.2 which points to the host machine's loopback interface
        private const val LOCAL_SERVER_URL = "https://10.0.2.2:8443/"
        
        // For physical device, use your machine's local IP address
        // private const val LOCAL_SERVER_URL = "https://192.168.1.100:8443/"

        fun create(context: Context): LocalTestApi {
            // Use dynamic certificate generator for local testing
            // This creates a fresh certificate for each client instance
            val dynamicGenerator = DynamicCertificateGenerator(context)
            
            // Create a client that only trusts our dynamic certificate
            // Hostname verification is disabled by default for local testing
            val okHttpClient = dynamicGenerator.createPinnedOkHttpClient(trustSystemCerts = false)
                .newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(LOCAL_SERVER_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(LocalTestApi::class.java)
        }
    }
}