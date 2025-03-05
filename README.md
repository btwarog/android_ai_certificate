# Dynamic Certificate Pinning Demo for Android

This project demonstrates how to implement dynamic certificate pinning in Android applications. Certificate pinning is a security technique that prevents man-in-the-middle attacks by validating that the server's certificate matches a known, trusted certificate. This implementation focuses on generating certificates dynamically on the fly without storing them.

## Implementation Details

This demo includes several approaches to certificate pinning:

1. **Dynamic Certificate Generation** - Creating secure certificates on the fly
2. **Hybrid Trust Management** - Combining dynamic certificates with system trust
3. **OkHttp Certificate Pinner** - Using certificate hash pinning with public APIs
4. **Certificate Hash Extraction** - Dynamically extracting certificate hashes during handshakes

## Key Components

- `DynamicCertificateGenerator.kt` - Generates secure certificates on demand
- `CertificatePinner.kt` - Custom implementation with dynamically generated certificates
- `PublicApiPinner.kt` - Implementation for public APIs with certificate hash pinning
- `ApiService.kt` - Public API service example with Retrofit
- `LocalTestApi.kt` - Local test API for testing dynamic certificates

## Testing the Certificate Pinning

### Testing with Dynamic Certificates

The application includes test scripts to help you set up a local test environment:

1. **Set Up a Local Test Server**
   ```bash
   # Navigate to the test_scripts directory
   cd test_scripts
   
   # Run the setup script to create a local HTTPS server
   ./setup_test_env.sh
   ```

2. **Run the App in an Emulator**
   - For emulators, the app is configured to connect to 10.0.2.2:8443
   - For physical devices, update the LOCAL_SERVER_URL in LocalTestApi.kt

### Testing Scenarios

#### 1. Testing with Public APIs
- The app uses standard system certificates for public APIs
- This ensures compatibility with well-known services
- You can also choose to use the certificate hash pinning option

#### 2. Testing with Dynamic Certificates
- For local server testing, the app generates a new certificate on each run
- The local API client only trusts this dynamic certificate
- This demonstrates how dynamic certificates can protect against MITM attacks

#### 3. Using Certificate Hash Extraction
- The PublicApiPinner includes a certificate hash extraction feature
- This allows you to see the hash of any server's certificate
- Useful for setting up certificate pinning with known servers

#### 4. Testing Against MITM Attacks
If you want to test with a proxy like Charles Proxy:

1. Install and configure Charles Proxy on your development machine
2. The requests to the local server should fail because only the dynamically generated certificate is trusted
3. The public API requests will succeed only if you install Charles Proxy's root CA on your device

## Debugging SSL Issues

To troubleshoot SSL issues, you can use:

```kotlin
// Add to your OkHttpClient builder
.hostnameVerifier { hostname, session -> 
    Log.d("SSL", "Verifying host: $hostname") 
    true // Careful! Only use this for debugging
}
```

## Certificate Hash Extraction

This project includes built-in tools for extracting certificate hashes, but you can also use the command line:

```bash
# For a domain
openssl s_client -servername example.com -connect example.com:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl base64

# For a local certificate
openssl x509 -in certificate.crt -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl base64
```

## Security Features

- **Dynamic Certificate Generation**: Creates new certificates on demand, no persistent storage
- **BouncyCastle Integration**: Uses industry-standard cryptography library
- **Fallback Mechanisms**: Graceful handling of provider availability issues
- **Hybrid Trust Management**: Option to trust both dynamic certificates and system CAs
- **Certificate Chain Support**: Can be extended to support CA-signed certificates

## Production Best Practices

1. **Dynamic Certificate Generation**
   - Ensure proper randomness and entropy for all generated certificates
   - Implement secure handling of the generated key material
   - Consider periodic certificate rotation for enhanced security

2. **Certificate Hash Pinning**
   - Pin to the public key hash, not the entire certificate
   - Include backup pins for certificate rotation
   - Implement remote update capability for pins

3. **Performance and Reliability**
   - Cache dynamically generated certificates when appropriate
   - Implement fallback mechanisms for different Android versions
   - Test thoroughly across multiple device types and network conditions

4. **Monitoring and Troubleshooting**
   - Log certificate validation events for security monitoring
   - Provide clear error messages when certificate validation fails
   - Consider analytics to track certificate-related issues

## References

- [BouncyCastle Cryptography Library](https://www.bouncycastle.org/java.html)
- [Android Network Security Configuration](https://developer.android.com/training/articles/security-config)
- [OkHttp Certificate Pinning](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/)
- [OWASP Certificate Pinning Guide](https://owasp.org/www-community/controls/Certificate_and_Public_Key_Pinning)