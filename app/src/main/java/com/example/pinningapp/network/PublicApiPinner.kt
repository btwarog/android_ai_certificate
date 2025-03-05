package com.example.pinningapp.network

import android.util.Log
import android.util.Base64
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Certificate pinning implementation for public APIs.
 * This approach allows pinning public certificates while maintaining compatibility with CA issuers.
 */
class PublicApiPinner {
    private val TAG = "PublicApiPinner"
    
    /**
     * Creates an OkHttpClient that uses certificate pinning with OkHttp's built-in CertificatePinner
     * This approach pins the specific server's certificate hash
     */
    fun createPinnedClient(hostname: String, vararg pinnedHashes: String): OkHttpClient {
        val certificatePinner = CertificatePinner.Builder()
            .add(hostname, *pinnedHashes)
            .build()
            
        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Creates a client that implements dynamic certificate verification
     * Instead of pinning in advance, this approach lets us verify certificates dynamically
     * and extract their hashes during the TLS handshake
     */
    fun createDynamicVerificationClient(): OkHttpClient {
        // Create a custom trust manager that logs certificate details
        val trustManager = object : X509TrustManager {
            // Get the system trust manager for normal verification
            private val systemTrustManager: X509TrustManager by lazy {
                val trustManagerFactory = javax.net.ssl.TrustManagerFactory.getInstance(
                    javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
                )
                trustManagerFactory.init(null as java.security.KeyStore?)
                trustManagerFactory.trustManagers.first() as X509TrustManager
            }
            
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                systemTrustManager.checkClientTrusted(chain, authType)
            }
            
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain == null || chain.isEmpty()) {
                    throw java.security.cert.CertificateException("Empty certificate chain")
                }
                
                // Log certificate information before validation
                val serverCert = chain[0]
                val publicKeyHash = calculateSha256Hash(serverCert.publicKey.encoded)
                val certHash = calculateSha256Hash(serverCert.encoded)
                
                Log.d(TAG, "Server: ${serverCert.subjectX500Principal.name}")
                Log.d(TAG, "Issuer: ${serverCert.issuerX500Principal.name}")
                Log.d(TAG, "Public key pin (SHA-256): sha256/$publicKeyHash")
                Log.d(TAG, "Certificate pin (SHA-256): sha256/$certHash")
                
                // Proceed with normal trust validation
                systemTrustManager.checkServerTrusted(chain, authType)
                
                // At this point, we could implement additional verification logic
                // such as comparing against known pins
            }
            
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return systemTrustManager.acceptedIssuers
            }
        }
        
        // Create SSL socket factory with our custom trust manager
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Helper function to calculate SHA-256 hash and encode it as Base64
     */
    private fun calculateSha256Hash(data: ByteArray): String {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digestBytes = messageDigest.digest(data)
        return Base64.encodeToString(digestBytes, Base64.NO_WRAP)
    }
}