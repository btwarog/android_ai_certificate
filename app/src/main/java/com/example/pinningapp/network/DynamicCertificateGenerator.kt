package com.example.pinningapp.network

import android.content.Context
import android.util.Log
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * Dynamic certificate generator that creates secure, on-the-fly certificates 
 * for SSL pinning without storing them persistently.
 */
class DynamicCertificateGenerator(private val context: Context) {
    private val TAG = "DynamicCertGen"
    
    init {
        // Register Bouncy Castle provider if not already registered
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
            Log.d(TAG, "Registered BouncyCastle provider")
        }
    }
    
    /**
     * Creates an OkHttpClient that uses a dynamically generated certificate
     * for pinning. A new certificate is generated for each client instance.
     * 
     * @param trustSystemCerts Whether to also trust system certificates (true) or only our dynamic cert (false)
     */
    fun createPinnedOkHttpClient(trustSystemCerts: Boolean = false): OkHttpClient {
        // Generate a self-signed certificate
        val (keyPair, certificate) = generateSelfSignedCertificate()
        
        // Create a trust manager that trusts our certificate and optionally system certs
        val trustManager = if (trustSystemCerts) {
            createTrustManagerWithSystemCerts(certificate)
        } else {
            createTrustManager(certificate)
        }
        
        // Create SSL context using our certificate
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        
        val builder = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
        
        if (!trustSystemCerts) {
            // For local testing, often we need to ignore hostname verification
            builder.hostnameVerifier { _, _ -> true }
        }
        
        return builder.build()
    }
    
    /**
     * Creates a trust manager that trusts both our dynamic certificate
     * and the system's trusted certificates
     */
    private fun createTrustManagerWithSystemCerts(certificate: X509Certificate): X509TrustManager {
        return object : X509TrustManager {
            // Get the system trust manager
            private val systemTrustManager: X509TrustManager = getTrustManager()
            
            // Get our custom trust manager
            private val customTrustManager: X509TrustManager = createTrustManager(certificate)
            
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                try {
                    customTrustManager.checkClientTrusted(chain, authType)
                } catch (e: Exception) {
                    systemTrustManager.checkClientTrusted(chain, authType)
                }
            }
            
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                try {
                    // First try with our custom certificate
                    customTrustManager.checkServerTrusted(chain, authType)
                } catch (e: Exception) {
                    try {
                        // If that fails, try with the system certificates
                        systemTrustManager.checkServerTrusted(chain, authType)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Certificate validation failed with both custom and system trust managers")
                        throw e2
                    }
                }
            }
            
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                // Return both our certificate and the system issuers
                val customIssuers = customTrustManager.acceptedIssuers
                val systemIssuers = systemTrustManager.acceptedIssuers
                return customIssuers + systemIssuers
            }
        }
    }
    
    /**
     * Gets the default system trust manager
     */
    private fun getTrustManager(): X509TrustManager {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?) // null = use system keystore
        return trustManagerFactory.trustManagers.first() as X509TrustManager
    }
    
    /**
     * Creates a trust manager that trusts only the given certificate
     */
    private fun createTrustManager(certificate: X509Certificate): X509TrustManager {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setCertificateEntry("ca", certificate)
        
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)
        
        return trustManagerFactory.trustManagers.first() as X509TrustManager
    }
    
    /**
     * Generates a self-signed X.509 certificate using BouncyCastle
     */
    fun generateSelfSignedCertificate(): Pair<KeyPair, X509Certificate> {
        try {
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
            calendar.add(Calendar.DAY_OF_YEAR, 1) // Valid for only 1 day
            val endDate = calendar.time
            
            // Generate a unique serial number
            val serialNumber = BigInteger(64, SecureRandom())
            
            // Create certificate subject/issuer
            val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
            nameBuilder.addRDN(BCStyle.CN, "Dynamic Certificate")
            nameBuilder.addRDN(BCStyle.O, "PinningApp")
            nameBuilder.addRDN(BCStyle.OU, "Security")
            nameBuilder.addRDN(BCStyle.L, "Temporary")
            nameBuilder.addRDN(BCStyle.C, "US")
            val subjectName = nameBuilder.build()
            
            // Create certificate builder
            val certificateBuilder = JcaX509v3CertificateBuilder(
                subjectName,                 // issuer (self-signed so same as subject)
                serialNumber,                // serial number
                startDate,                   // start date
                endDate,                     // end date
                subjectName,                 // subject
                keyPair.public               // public key
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
            Log.e(TAG, "Error generating certificate with BC: ${e.message}", e)
            throw RuntimeException("Failed to generate certificate using BouncyCastle", e)
        }
    }
}