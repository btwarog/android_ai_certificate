package com.example.pinningapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pinningapp.network.ApiService
import com.example.pinningapp.network.LocalTestApi
import com.example.pinningapp.network.Todo
import com.example.pinningapp.ui.theme.PinningAppTheme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : ComponentActivity() {
    
    private val TAG = "CertPinning"
    private val responseState = mutableStateOf("No requests made yet")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            PinningAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CertificatePinningDemo(
                        modifier = Modifier.padding(innerPadding),
                        responseState = responseState,
                        onTestPublicApi = { testPublicApi() },
                        onTestLocalApi = { testLocalServer() }
                    )
                }
            }
        }
    }
    
    private fun testPublicApi() {
        responseState.value = "Testing with public API..."
        val apiService = ApiService.create(this)
        
        apiService.getTodo().enqueue(object : Callback<Todo> {
            override fun onResponse(call: Call<Todo>, response: Response<Todo>) {
                if (response.isSuccessful) {
                    val message = "Public API call successful: ${response.body()?.title}"
                    Log.d(TAG, message)
                    responseState.value = message
                } else {
                    val message = "Public API call failed: ${response.code()}"
                    Log.e(TAG, message)
                    responseState.value = message
                }
            }
            
            override fun onFailure(call: Call<Todo>, t: Throwable) {
                val message = "Public API call failed: ${t.message}"
                Log.e(TAG, message, t)
                responseState.value = message
            }
        })
    }
    
    private fun testLocalServer() {
        responseState.value = "Testing with local server..."
        val localApi = LocalTestApi.create(this)
        
        localApi.getTodo().enqueue(object : Callback<Todo> {
            override fun onResponse(call: Call<Todo>, response: Response<Todo>) {
                if (response.isSuccessful) {
                    val message = "Local server call successful: ${response.body()?.title}"
                    Log.d(TAG, message)
                    responseState.value = message
                } else {
                    val message = "Local server call failed: ${response.code()}"
                    Log.e(TAG, message)
                    responseState.value = message
                }
            }
            
            override fun onFailure(call: Call<Todo>, t: Throwable) {
                val message = "Local server call failed: ${t.message}"
                Log.e(TAG, message, t)
                responseState.value = message
            }
        })
    }
}

@Composable
fun CertificatePinningDemo(
    modifier: Modifier = Modifier, 
    responseState: MutableState<String>,
    onTestPublicApi: () -> Unit,
    onTestLocalApi: () -> Unit
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Certificate Pinning Demo",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "This app demonstrates certificate pinning in Android.",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onTestPublicApi) {
            Text("Test with Public API")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(onClick = onTestLocalApi) {
            Text("Test with Local Server")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Response:",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = responseState.value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}