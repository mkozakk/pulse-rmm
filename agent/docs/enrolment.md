# Enrolment (`internal/enrolment/`)

Handles the initial registration handshake with the backend server.

### code
[`enrol.go`](../internal/enrolment/enrol.go):
	- `Enrol` - Performs a one-time gRPC request to authenticate the installation token, exchange cryptographic keys, and officially register the device.

### description
The enrolment process is executed only once during the agent's lifecycle, specifically when it boots up and cannot find a locally saved endpoint ID file. Its purpose is to authenticate the new device with the infrastructure and obtain a permanent unique identifier. The `Enrol` logic establishes a temporary gRPC connection to the gateway using `grpc.NewClient`. It then constructs an `EnrolRequest` protobuf message. This message bundles the administrator-provided enrolment token along with essential hardware context obtained via `os.Hostname()` and the `runtime` package (`GOOS` and `GOARCH`). Crucially, it also extracts the public half of the agent's Ed25519 keypair and embeds the raw bytes into the request, which the backend will store and use for future cryptographic verification. Upon successful validation of the token, the server responds with a generated endpoint UUID. The gRPC connection is then closed via `defer conn.Close()`, and the UUID is returned to the main process where it is committed to local disk storage.
