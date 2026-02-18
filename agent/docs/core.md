# Core Initialization (`main.go`)

The main entry point of the Pulse RMM Agent, responsible for bootstrapping the application.

### code
[`main.go`](../main.go):
	- `main` - Serves as the absolute entry point of the binary, parsing configuration and orchestrating the entire startup sequence.
	- `runControlStream` - Manages the blocking loop for the gRPC stream, sustaining a persistent connection to receive incoming server commands.
	- `dispatchCmd` - Inspects protobuf command payloads and routes incoming messages to the appropriate functional modules.
	- `runHeartbeat`, `runMetrics`, `runSoftwareScan` - Execute infinite background loops using `time.Ticker` to continuously synchronize telemetry and inventory.

### description
The initialization sequence begins by reading necessary configuration from `os.Getenv` to locate the backend servers. The agent then attempts to load its cryptographic identity and endpoint ID from local storage. If these are missing, it assumes this is a fresh installation and synchronously initiates the enrolment process using the provided token. Once the agent has established its identity, it sets up persistent gRPC connections using `grpc.NewClient` with insecure transport credentials for local testing. It then leverages Go's concurrency model by spawning several background `goroutines`. These goroutines run isolated infinite loops governed by `time.Ticker` and `context.Context` to handle routine operations like transmitting periodic heartbeats, pushing hardware metrics, and scanning installed software. Finally, the main thread's execution is blocked by invoking `runControlStream`, which establishes a persistent bidirectional connection to the server. When the control stream receives a command, it is passed to `dispatchCmd`, which uses a type switch on the protobuf payload to invoke the appropriate subsystem securely.
