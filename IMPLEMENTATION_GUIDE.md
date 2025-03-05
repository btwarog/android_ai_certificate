# Dynamic Certificate Pinning Implementation Guide

This guide explains how to implement dynamic certificate pinning in Android applications, with a focus on generating certificates on the fly without persistent storage.

## Implementation Approaches

### 1. Dynamic Certificate Generation with BouncyCastle

Generate secure certificates dynamically using the BouncyCastle library:

```kotlin
// Add dependencies
implementation("org.bouncycastle:bcprov-jdk18on:1.77")
implementation("org.bouncycastle:bcpkix-jdk18on:1.77")

// Register the provider in your Application class
Security.insertProviderAt(BouncyCastleProvider(), 1)

// Generate a certificate dynamically
fun generateSelfSignedCertificate(): Pair<KeyPair, X509Certificate> {
    // Generate key pair
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC")
    keyPairGenerator.initialize(2048, SecureRandom())
    val keyPair = keyPairGenerator.generateKeyPair()
    
    // Set certificate properties
    val startDate = Date()
    val endDate = Date(startDate.time + TimeUnit.DAYS.toMillis(1))
    val serialNumber = BigInteger(64, SecureRandom())
    
    // Create certificate subject
    val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
    nameBuilder.addRDN(BCStyle.CN, "Dynamic Certificate")
    nameBuilder.addRDN(BCStyle.O, "YourApp")
    val subjectName = nameBuilder.build()
    
    // Build certificate
    val certificateBuilder = JcaX509v3CertificateBuilder(
        subjectName, serialNumber, startDate, endDate, subjectName, keyPair.public
    )
    
    // Add extensions
    certificateBuilder.addExtension(
        Extension.basicConstraints, true, BasicConstraints(false)
    )
    
    // Sign the certificate
    val contentSigner = JcaContentSignerBuilder("SHA256WithRSA")
        .setProvider("BC")
        .build(keyPair.private)
    
    val certificate = JcaX509CertificateConverter()
        .setProvider("BC")
        .getCertificate(certificateBuilder.build(contentSigner))
    
    return Pair(keyPair, certificate)
}
```

### 2. Implementing a Dynamic Trust Manager

Create a custom trust manager that uses your dynamically generated certificate:

```kotlin
// Create a trust manager that only trusts our dynamic certificate
fun createTrustManager(certificate: X509Certificate): X509TrustManager {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(null, null)
    keyStore.setCertificateEntry("ca", certificate)
    
    val trustManagerFactory = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm()
    )
    trustManagerFactory.init(keyStore)
    
    return trustManagerFactory.trustManagers.first() as X509TrustManager
}

// Create an OkHttpClient with the trust manager
val (_, certificate) = generateSelfSignedCertificate()
val trustManager = createTrustManager(certificate)

val sslContext = SSLContext.getInstance("TLS")
sslContext.init(null, arrayOf(trustManager), null)

val client = OkHttpClient.Builder()
    .sslSocketFactory(sslContext.socketFactory, trustManager)
    .build()
```

### 3. Hybrid Trust Manager (System + Dynamic Certificates)

For maximum flexibility, create a trust manager that tries both your dynamic certificate and the system's trusted certificates:

```kotlin
// Create a hybrid trust manager
fun createHybridTrustManager(certificate: X509Certificate): X509TrustManager {
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
                // First try with our dynamic certificate
                customTrustManager.checkServerTrusted(chain, authType)
            } catch (e: Exception) {
                // Fall back to system certificates
                systemTrustManager.checkServerTrusted(chain, authType)
            }
        }
        
        override fun getAcceptedIssuers(): Array<X509Certificate> {
            // Combine our certificate with system issuers
            return customTrustManager.acceptedIssuers + systemTrustManager.acceptedIssuers
        }
    }
}
```

## Certificate Hash Extraction and Verification

### Extracting Certificate Hashes Programmatically

You can extract certificate hashes during the SSL handshake:

```kotlin
// Create a certificate hash extractor
fun createCertificateExtractor(): X509TrustManager {
    return object : X509TrustManager {
        private val systemTrustManager: X509TrustManager = getTrustManager()
        
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            systemTrustManager.checkClientTrusted(chain, authType)
        }
        
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain == null || chain.isEmpty()) {
                throw java.security.cert.CertificateException("Empty certificate chain")
            }
            
            // Extract and log certificate information
            val serverCert = chain[0]
            val publicKeyHash = calculateSha256Hash(serverCert.publicKey.encoded)
            val certHash = calculateSha256Hash(serverCert.encoded)
            
            Log.d("CertExtractor", "Server: ${serverCert.subjectX500Principal.name}")
            Log.d("CertExtractor", "Public key pin: sha256/$publicKeyHash")
            
            // Proceed with normal validation
            systemTrustManager.checkServerTrusted(chain, authType)
        }
        
        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return systemTrustManager.acceptedIssuers
        }
        
        private fun calculateSha256Hash(data: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data)
            return Base64.encodeToString(hash, Base64.NO_WRAP)
        }
    }
}
```

### Using Command Line Tools

You can also extract certificate hashes using OpenSSL:

```bash
# For a live website
openssl s_client -servername example.com -connect example.com:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl base64

# For a local certificate file
openssl x509 -in certificate.crt -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl base64
```

## Production Best Practices

1. **Dynamic Certificate Security**
   - Use secure random number generation with adequate entropy
   - Implement proper key size (RSA 2048+ bits recommended)
   - Consider certificate lifetime based on your security requirements

2. **Error Handling and Fallbacks**
   - Implement robust error handling for cryptographic operations
   - Include provider fallbacks for different Android versions
   - Consider graceful degradation if certificate generation fails

3. **Performance Considerations**
   - Certificate generation is computationally expensive
   - Consider caching generated certificates in memory
   - For high-traffic scenarios, implement certificate reuse strategies

4. **Integration with Existing Systems**
   - Ensure compatibility with your existing authentication mechanisms
   - Consider how dynamic certificates affect your backend validation
   - Document the security model for your team

## Common Challenges with Dynamic Certificates

1. **BouncyCastle Integration Issues**
   - Register the BouncyCastle provider early in application lifecycle
   - Use proper algorithm names (e.g., "SHA256WithRSA" not "sha256withrsa")
   - Implement provider fallbacks for compatibility across devices

2. **Memory Management**
   - Be aware of memory usage when generating certificates frequently
   - Consider a certificate cache with appropriate lifecycle management
   - Properly clean up cryptographic resources

3. **Security of Generated Certificates**
   - Protect private keys generated on the device
   - Implement secure storage if keys need to be persisted
   - Use adequate key sizes and strong signature algorithms

4. **Network Reliability**
   - Test dynamic certificate pinning with poor network conditions
   - Ensure proper handling of connection failures
   - Implement appropriate timeout handling

5. **Testing and Debugging**
   - Log certificate details during development
   - Create a debug mode to help identify certificate issues
   - Test with network intercepting proxies to verify security

## Implementation Examples

### Complete OkHttp Integration Example

```kotlin
class SecureNetworkClient(private val context: Context) {
    // Create a client with dynamic certificate pinning
    fun createSecureClient(): OkHttpClient {
        // Generate a fresh certificate
        val dynamicCertificate = generateDynamicCertificate()
        
        // Create a trust manager that uses our dynamic certificate
        val trustManager = createTrustManager(dynamicCertificate)
        
        // Create SSL context with our trust manager
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        
        // Build the OkHttpClient
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
```

## Learn More

- [BouncyCastle Library Documentation](https://www.bouncycastle.org/documentation.html)
- [OWASP Certificate Pinning Guide](https://owasp.org/www-community/controls/Certificate_and_Public_Key_Pinning)
- [Android Network Security Configuration](https://developer.android.com/training/articles/security-config)
- [OkHttp Certificate Pinning](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/)
- [Android Keystore System](https://developer.android.com/training/articles/keystore)