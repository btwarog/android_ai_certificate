package com.example.pinningapp.network

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

// BouncyCastle imports
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * Certificate pinner class that uses static certificates loaded from resources
 */
class CertificatePinner(private val context: Context) {
    private val TAG = "CertificatePinner"

    /**
     * Creates an OkHttpClient with certificate pinning using a dynamically generated certificate
     */
    fun createPinnedOkHttpClient(): OkHttpClient {
        // Generate a dynamic certificate
        val (_, certificate) = generateSelfSignedCertificate()
        val certificateInputStream = ByteArrayInputStream(certificate.encoded)
        
        // Get the trust manager using the certificate
        val trustManager = getTrustManager(certificateInputStream)
        
        // Create an SSL context with our trust manager
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        
        // Build the OkHttpClient with our custom SSL socket factory
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .build()
    }
    
    /**
     * Creates a trust manager that trusts only the provided certificate
     */
    private fun getTrustManager(certificateInputStream: InputStream): X509TrustManager {
        // Create a certificate factory
        val certificateFactory = CertificateFactory.getInstance("X.509")
        
        // Generate the certificate
        val certificate = certificateFactory.generateCertificate(certificateInputStream) as X509Certificate
        certificateInputStream.close()
        
        // Create a KeyStore containing our trusted certificates
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setCertificateEntry("ca", certificate)
        
        // Create a TrustManager that trusts the certificates in our KeyStore
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)
        
        return trustManagerFactory.trustManagers.first() as X509TrustManager
    }
    
    /**
     * Generates a new self-signed X.509 certificate on the fly using BouncyCastle.
     * 
     * @return A pair containing the generated KeyPair and X509Certificate
     */
    private fun generateSelfSignedCertificate(): Pair<KeyPair, X509Certificate> {
        try {
            // Register BouncyCastle provider if not already registered
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
            
            // Generate key pair - try with BC provider first, fall back to default if needed
            val keyPair = try {
                val keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC")
                keyPairGenerator.initialize(2048, SecureRandom())
                keyPairGenerator.generateKeyPair()
            } catch (e: Exception) {
                Log.w(TAG, "Falling back to default provider for key generation: ${e.message}")
                val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
                keyPairGenerator.initialize(2048, SecureRandom())
                keyPairGenerator.generateKeyPair()
            }
            
            // Set certificate validity period
            val startDate = Date()
            val calendar = Calendar.getInstance()
            calendar.time = startDate
            calendar.add(Calendar.DAY_OF_YEAR, 365) // Valid for 1 year
            val endDate = calendar.time
            
            // Generate a unique serial number
            val serialNumber = BigInteger(64, SecureRandom())
            
            // Create certificate subject/issuer using BouncyCastle classes
            val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
            nameBuilder.addRDN(BCStyle.CN, "DynamicSecurityCert")
            nameBuilder.addRDN(BCStyle.O, "PinningApp")
            nameBuilder.addRDN(BCStyle.L, "Local")
            nameBuilder.addRDN(BCStyle.C, "US")
            val subjectName = nameBuilder.build()
            
            // Create certificate builder with BouncyCastle
            val certificateBuilder = JcaX509v3CertificateBuilder(
                subjectName,           // issuer (self-signed so same as subject)
                serialNumber,          // serial number
                startDate,             // start date
                endDate,               // end date
                subjectName,           // subject
                keyPair.public         // public key
            )
            
            // Add extensions
            // Basic constraints: not a CA
            certificateBuilder.addExtension(
                Extension.basicConstraints, 
                true, 
                BasicConstraints(false)
            )
            
            // Key usage: digital signature and key encipherment
            certificateBuilder.addExtension(
                Extension.keyUsage,
                true,
                KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
            )
            
            // Create content signer and build certificate - try with BC provider first, fall back to default if needed
            val contentSigner = try {
                JcaContentSignerBuilder("SHA256WithRSA")
                    .setProvider("BC")
                    .build(keyPair.private)
            } catch (e: Exception) {
                Log.w(TAG, "Falling back to default provider for content signer: ${e.message}")
                JcaContentSignerBuilder("SHA256WithRSA")
                    .build(keyPair.private)
            }
            
            // Convert to certificate - try with BC provider first, fall back to default if needed
            val certificate = try {
                JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certificateBuilder.build(contentSigner))
            } catch (e: Exception) {
                Log.w(TAG, "Falling back to default provider for certificate conversion: ${e.message}")
                JcaX509CertificateConverter()
                    .getCertificate(certificateBuilder.build(contentSigner))
            }
            
            Log.d(TAG, "Generated dynamic certificate with serial: ${certificate.serialNumber}")
            return Pair(keyPair, certificate)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating certificate: ${e.message}", e)
            throw RuntimeException("Certificate generation failed", e)
        }
    }
}