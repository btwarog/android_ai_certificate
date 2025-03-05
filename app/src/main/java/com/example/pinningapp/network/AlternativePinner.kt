package com.example.pinningapp.network

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

/**
 * Alternative implementation using OkHttp's built-in certificate pinner.
 * This approach pins based on the certificate's public key hash.
 */
object AlternativePinner {
    
    // Example domain to pin
    private const val HOSTNAME = "jsonplaceholder.typicode.com"
    
    // Actual SHA-256 hash of the certificate's public key for jsonplaceholder.typicode.com
    private const val PUBLIC_KEY_HASH = "sha256/eU8uhA6QAxYsY/XmB8tU2nji6+Ccm1bkv+byr04/qwg="
    
    fun createPinnedOkHttpClient(): OkHttpClient {
        val certificatePinner = CertificatePinner.Builder()
            .add(HOSTNAME, PUBLIC_KEY_HASH)
            .build()
            
        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .build()
    }
    
    /**
     * To find the actual public key hash of a certificate, you can use:
     * 
     * For a domain:
     * ```
     * openssl s_client -servername jsonplaceholder.typicode.com -connect jsonplaceholder.typicode.com:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl base64
     * ```
     * 
     * For a local certificate:
     * ```
     * openssl x509 -in certificate.crt -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl base64
     * ```
     */
}