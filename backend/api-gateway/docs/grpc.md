# gRPC Control Plane (`api/internal/`, `infrastructure/grpc/`, `infrastructure/desktop/`)

Manages the persistent communication streams connecting endpoint agents to the backend, enabling real-time command delivery and telemetry ingestion.

### code
**Internal REST Controllers**
[`SoftwareCommandInternalController.java`](../src/main/java/dev/pulsermm/gateway/api/internal/SoftwareCommandInternalController.java), [`ScriptCommandInternalController.java`](../src/main/java/dev/pulsermm/gateway/api/internal/ScriptCommandInternalController.java), & [`DesktopSessionInternalController.java`](../src/main/java/dev/pulsermm/gateway/api/internal/DesktopSessionInternalController.java):
	- Expose private, unauthenticated HTTP endpoints on the internal network allowing the Software, Script, and Remote services to initiate asynchronous tasks (like installing packages or preparing remote sessions) that must be forwarded to the connected agents.

**gRPC Infrastructure & Streams**
[`GrpcServiceConfig.java`](../src/main/java/dev/pulsermm/gateway/infrastructure/grpc/GrpcServiceConfig.java) & [`AgentServiceGrpcServer.java`](../src/main/java/dev/pulsermm/gateway/infrastructure/grpc/AgentServiceGrpcServer.java):
	- Establish and configure the embedded gRPC server tailored specifically to listen on a dedicated port for incoming agent connections.
[`GatewayGrpcService.java`](../src/main/java/dev/pulsermm/gateway/infrastructure/grpc/GatewayGrpcService.java):
	- `openAgentStream` - Accepts the initial connection from a physical endpoint, establishing a long-lived, bidirectional stream that remains open indefinitely for command and telemetry transfer.

**Registries & State Tracking**
[`AgentRegistry.java`](../src/main/java/dev/pulsermm/gateway/infrastructure/grpc/AgentRegistry.java):
	- `register`, `remove`, & `sendCommand` - Act as an in-memory, thread-safe map linking endpoint UUIDs to their active `StreamObserver` objects, ensuring the Gateway knows exactly which network pipe leads to which machine.
[`PendingCommandRegistry.java`](../src/main/java/dev/pulsermm/gateway/infrastructure/grpc/PendingCommandRegistry.java):
	- Tracks dispatched instructions that require a delayed response (like an asynchronous software installation), matching incoming acknowledgments from the agent back to the original request context.

**Dispatchers & Observers**
[`ScriptCommandDispatcher.java`](../src/main/java/dev/pulsermm/gateway/infrastructure/grpc/ScriptCommandDispatcher.java), [`SoftwareCommandDispatcher.java`](../src/main/java/dev/pulsermm/gateway/infrastructure/grpc/SoftwareCommandDispatcher.java), & [`DesktopSessionDispatcher.java`](../src/main/java/dev/pulsermm/gateway/infrastructure/desktop/DesktopSessionDispatcher.java):
	- `dispatch` - Provide cleanly separated interfaces translating domain-specific instructions (like a script payload or WebRTC preparation request) into strictly typed Protobuf `GatewayCommand` messages before pushing them down the stream via the registry.
[`AgentEventObserver.java`](../src/main/java/dev/pulsermm/gateway/infrastructure/grpc/AgentEventObserver.java):
	- `onNext` & `onError` - Intercept all incoming data from the agents (like telemetry pings, command outputs, or error states) and act as a reverse router, forwarding the payloads to internal message queues or REST controllers based on their Protobuf event type.

### description
Endpoints deployed in diverse corporate environments cannot reliably accept incoming network connections due to NATs and firewalls. To solve this, agents proactively connect outward to the `AgentServiceGrpcServer` configured by `GrpcServiceConfig`. The `GatewayGrpcService` accepts this connection, establishing a persistent bidirectional stream. Because the infrastructure might scale horizontally, the system requires a mechanism to know which agent is connected to which server. The `AgentRegistry` fulfills this role, maintaining the active link between endpoint UUIDs and their network streams. 

When an internal backend service (e.g., the Script Service) needs to execute a command, it cannot communicate via gRPC directly. Instead, it sends a standard HTTP POST to one of the internal controllers, such as the `ScriptCommandInternalController`. This controller invokes a specialized dispatcher (`ScriptCommandDispatcher`), which queries the registry, locates the correct active stream, encapsulates the payload into a Protobuf message, and pushes it down to the agent. If the command takes time, the `PendingCommandRegistry` holds the context. Conversely, when the agent responds with command output or telemetry, the `AgentEventObserver` catches the incoming stream data. It parses the Protobuf type and acts as a reverse router, forwarding the data internally so the correct microservice can update its database.
