# Control Stream (`internal/control/`)

Manages the persistent communication channel between the agent and the backend.

### code
[`stream.go`](../internal/control/stream.go):
	- `Run` - Dials the API gateway and manages bidirectional data flow, establishing a robust, long-lived connection to seamlessly bypass NATs and firewalls.

### description
To overcome common networking obstacles like strict corporate firewalls, the agent does not listen for incoming connections on a local port. Instead, it proactively reaches out to the gateway using `grpc.NewClient` to establish a long-lived, bidirectional gRPC stream. This stream serves as the primary control plane for the application. Upon establishing the connection, the agent immediately transmits an `AgentHello` protobuf message containing its endpoint ID and version to authenticate the session. The system then heavily relies on Go channels (`chan`) and `select` statements to manage asynchronous, non-blocking communication. A dedicated background goroutine is launched to continuously call `stream.Recv()`. When commands arrive from the server, this goroutine pushes them into the `inCh` (inbox) channel for the main application dispatcher to process. Simultaneously, the primary routing loop blocks on a `select` statement waiting for the `outCh` (outbox) channel. Whenever internal agent modules (like the script executor or shell proxy) push execution results into the outbox, the loop unblocks and uses `stream.Send()` to transmit the events back to the backend.
