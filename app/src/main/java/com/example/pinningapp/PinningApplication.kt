package com.example.pinningapp

import android.app.Application
import android.util.Log
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class PinningApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Register the BouncyCastle provider at application startup
        try {
            // Remove any existing provider first to avoid conflicts
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            
            // Add the provider at position 1 (highest priority)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
            
            // Double check if the provider is available
            val provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
            if (provider != null) {
                Log.d("PinningApplication", "BouncyCastle provider successfully registered: ${provider.name} v${provider.version}")
                
                // List all available algorithms for debugging
                val algorithms = provider.services.map { it.algorithm }.distinct().sorted()
                Log.d("PinningApplication", "Available algorithms: ${algorithms.take(10)}...")
            } else {
                Log.e("PinningApplication", "Failed to register BouncyCastle provider")
            }
        } catch (e: Exception) {
            Log.e("PinningApplication", "Error initializing BouncyCastle: ${e.message}", e)
        }
    }
}