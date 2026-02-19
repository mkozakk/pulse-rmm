# API Gateway (`backend/api-gateway/`)

The API Gateway is the singular ingress point for the Pulse RMM infrastructure. It securely routes frontend HTTP requests, proxies interactive WebSockets, and manages the persistent gRPC control streams connecting the backend to the endpoint agents.

## Directory Structure
```text
api-gateway/
├── docs/                   # Full documentation of gateway features
├── src/main/java/.../
│   ├── api/                # HTTP filters, security configuration, internal controllers, and WebSockets
│   ├── config/             # Spring Boot configuration classes (e.g., WebSocket config)
│   └── infrastructure/     # gRPC servers, routing registries, dispatchers, and identity clients
├── src/test/.../           # Unit and integration tests
├── Dockerfile              # Containerization definition
└── pom.xml                 # Maven dependencies
```

## Features & Internal Documentation

* **[Security & RBAC](docs/security.md)** - Explains perimeter authentication, permission guards, and communication with the identity service.
* **[gRPC Control Plane](docs/grpc.md)** - Details the management of persistent agent streams, command registries, and internal dispatchers.
* **[WebSocket Proxies](docs/websocket.md)** - Covers the bridging of browser-based WebSockets for shells and remote desktop directly to agent gRPC streams.
