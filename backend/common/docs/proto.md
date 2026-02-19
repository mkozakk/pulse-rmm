# Generated Protobuf Stubs (`src/generated/`)

Houses the automatically compiled Java classes representing the gRPC and Protocol Buffer contracts.

### code
[`AgentServiceGrpc.java`](../src/generated/dev/pulsermm/proto/v1/AgentServiceGrpc.java) & [`GatewayServiceGrpc.java`](../src/generated/dev/pulsermm/proto/v1/GatewayServiceGrpc.java):
	- `AgentServiceStub` & `GatewayServiceStub` - Act as the client-side proxy classes required to initiate remote procedure calls between the backend services and the endpoint agents.
	- `AgentServiceImplBase` & `GatewayServiceImplBase` - Provide the abstract base classes that backend controllers must extend to receive and handle incoming gRPC requests.

[`GatewayCommand.java`](../src/generated/dev/pulsermm/proto/v1/GatewayCommand.java) & [`AgentEvent.java`](../src/generated/dev/pulsermm/proto/v1/AgentEvent.java):
	- `GatewayCommand` - Encapsulates all possible server-to-agent instructions (like starting a shell or running a script) into a single, strictly typed data structure.
	- `AgentEvent` - Standardizes the structure of all agent-to-server responses (like command acknowledgments or shell output) to ensure consistent parsing on the backend.

### description
To ensure reliable, high-performance communication between the Go agents and the Java backend, the system utilizes gRPC and Protocol Buffers. Instead of manually writing JSON parsers, the system defines the API contract in language-agnostic `.proto` files. During the Maven build process (`mvn compile`), the `protobuf-maven-plugin` intercepts these definitions and automatically generates hundreds of Java classes. These generated files reside entirely within the `common` module. By centralizing these stubs here, every other backend microservice can declare a dependency on the `common` module and immediately gain access to the strictly typed request, response, and service definition classes. This guarantees that if the contract changes, any incompatible code across the entire backend will fail to compile immediately, preventing runtime serialization errors.
