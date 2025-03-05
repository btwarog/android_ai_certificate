#!/bin/bash

# Create a directory for test certificates
mkdir -p test_certs
cd test_certs

echo "Creating self-signed certificates for testing..."

# Create a valid certificate (should match what's in the app)
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout valid_key.pem -out valid_cert.pem \
    -subj "/CN=example.com/O=Example Company/C=US"

# Create an invalid certificate (to simulate MITM attack)
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout invalid_key.pem -out invalid_cert.pem \
    -subj "/CN=example.com/O=Attacker/C=US"

echo "Getting certificate hashes for OkHttp pinner..."

# Get hash of valid certificate
echo "Valid certificate hash:"
openssl x509 -in valid_cert.pem -pubkey -noout | \
    openssl pkey -pubin -outform der | \
    openssl dgst -sha256 -binary | \
    openssl base64

# Get hash of invalid certificate
echo "Invalid certificate hash:"
openssl x509 -in invalid_cert.pem -pubkey -noout | \
    openssl pkey -pubin -outform der | \
    openssl dgst -sha256 -binary | \
    openssl base64

echo "Creating a simple test API server..."
cat > test_server.js << 'EOF'
const https = require('https');
const fs = require('fs');

// Certificate options
const options = {
  key: fs.readFileSync('valid_key.pem'),
  cert: fs.readFileSync('valid_cert.pem')
};

// Create a simple HTTPS server
const server = https.createServer(options, (req, res) => {
  if (req.url === '/todos/1') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      userId: 1,
      id: 1,
      title: "Certificate pinning test successful",
      completed: false
    }));
  } else {
    res.writeHead(404);
    res.end();
  }
});

const PORT = 8443;
server.listen(PORT, () => {
  console.log(`HTTPS Server running on port ${PORT}`);
  console.log(`Test API available at: https://localhost:${PORT}/todos/1`);
  console.log(`To test the invalid certificate, run: node test_server_invalid.js`);
});
EOF

# Create a server with the invalid certificate for testing
cat > test_server_invalid.js << 'EOF'
const https = require('https');
const fs = require('fs');

// Certificate options (using the INVALID certificate)
const options = {
  key: fs.readFileSync('invalid_key.pem'),
  cert: fs.readFileSync('invalid_cert.pem')
};

// Create a simple HTTPS server
const server = https.createServer(options, (req, res) => {
  if (req.url === '/todos/1') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      userId: 1,
      id: 1,
      title: "MITM ATTACK SUCCESSFUL - Your certificate pinning failed!",
      completed: false
    }));
  } else {
    res.writeHead(404);
    res.end();
  }
});

const PORT = 8444;
server.listen(PORT, () => {
  console.log(`HTTPS Server with INVALID certificate running on port ${PORT}`);
  console.log(`Test API available at: https://localhost:${PORT}/todos/1`);
  console.log(`This server simulates a man-in-the-middle attack.`);
  console.log(`If your app connects to this server, your certificate pinning is not working correctly.`);
});
EOF

echo "Setup complete!"
echo "To run the test server with the valid certificate:"
echo "  cd test_certs && node test_server.js"
echo ""
echo "To run the test server with the invalid certificate (to test your pinning):"
echo "  cd test_certs && node test_server_invalid.js"
echo ""
echo "Don't forget to modify your app to point to your local test server instead of the remote API."
echo "Replace the base URL with: https://10.0.2.2:8443/ for Android emulator"
echo "or your machine's local IP address if using a physical device."