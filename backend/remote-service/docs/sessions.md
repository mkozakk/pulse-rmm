# Session Lifecycle (`api/`, `application/`, `domain/`, `infrastructure/`)

Manages the secure initiation, state tracking, and token validation of interactive remote control features like Shells and Desktop sharing.

### code
**API & Routing**
[`SessionController.java`](../src/main/java/dev/pulsermm/remote/api/SessionController.java):
	- `createSession` & `getSessionStatus` - Expose REST endpoints allowing the frontend web application to request the initiation of a new interactive session and poll its current connectivity state.

**Data Transfer Objects (DTOs)**
[`CreateSessionRequest.java`](../src/main/java/dev/pulsermm/remote/api/dto/CreateSessionRequest.java) & [`CreateSessionResponse.java`](../src/main/java/dev/pulsermm/remote/api/dto/CreateSessionResponse.java):
	- Encapsulate the inbound payload defining the target endpoint UUID and requested session type (e.g., "shell" or "desktop"), and return the critical, cryptographically secure one-time token required to open the WebSocket.
[`SessionStatusResponse.java`](../src/main/java/dev/pulsermm/remote/api/dto/SessionStatusResponse.java):
	- Structures the outbound JSON reflecting the real-time operational state of the session (e.g., Pending, Active, Closed).

**Domain Exceptions**
[`SessionNotFoundException.java`](../src/main/java/dev/pulsermm/remote/application/SessionNotFoundException.java):
	- Thrown when a polling request or the Gateway attempts to validate a token associated with a session that has either expired, been closed, or does not exist, triggering a standard 404 response.

**Application Logic & Integrations**
[`SessionService.java`](../src/main/java/dev/pulsermm/remote/application/SessionService.java):
	- The core orchestrator that validates incoming requests, guarantees the target endpoint is online, evaluates RBAC permissions, and generates the temporary tokens to authorize the connection.
[`GatewayClient.java`](../src/main/java/dev/pulsermm/remote/infrastructure/GatewayClient.java):
	- An internal HTTP client utilized to proactively query the API Gateway to confirm the target agent actually has an active gRPC stream before attempting to initialize a complex WebRTC or shell connection.
[`IdentityClient.java`](../src/main/java/dev/pulsermm/remote/infrastructure/IdentityClient.java):
	- An internal HTTP client used to verify that the requesting administrator possesses the explicit granular permissions (like `remote:desktop`) required to interact with the target machine.

**Domain Entities & Repositories**
[`DesktopSession.java`](../src/main/java/dev/pulsermm/remote/domain/DesktopSession.java):
	- The core domain entity representing an authorized attempt to remotely view or control a specific endpoint. It stores the generated one-time token, the session type, and a strict expiration timestamp.
[`SessionRepository.java`](../src/main/java/dev/pulsermm/remote/infrastructure/persistence/SessionRepository.java):
	- Interfaces with the database to persist the session tracking records and retrieve them efficiently when the API Gateway attempts to validate a token during the WebSocket handshake.

### description
Interactive features like Remote Desktop and Terminal Shells bypass standard stateless HTTP mechanics, relying instead on long-lived WebSocket connections proxied through the API Gateway. To secure these persistent connections, the system requires a specialized, stateful authorization mechanism managed by the Remote Service. When an administrator clicks "Connect" in the web application, the frontend issues a `CreateSessionRequest` to the `SessionController`. 

The `SessionService` intercepts this and begins a multi-step verification process. First, it uses the `IdentityClient` to verify the user has the required RBAC permissions. Next, it utilizes the `GatewayClient` to ensure the target endpoint is currently online and capable of receiving commands. If authorized and online, the service generates a temporary, highly secure random token and persists a `DesktopSession` entity to the database via the `SessionRepository`, setting a strict expiration time (e.g., 30 seconds). 

The service returns this token to the frontend via `CreateSessionResponse`. The frontend then immediately attempts to open a WebSocket connection to the API Gateway, injecting this temporary token into the initial upgrade request. The Gateway pauses the handshake, performs an internal HTTP call back to the Remote Service to validate the token against the database, and only upon success does it bridge the browser's WebSocket to the agent's gRPC stream, allowing the WebRTC signaling or interactive shell session to commence securely.
