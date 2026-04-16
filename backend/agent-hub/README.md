# Agent Hub (`backend/agent-hub/`)

The Agent Hub is the control plane for all endpoint agents in the Pulse RMM system. It maintains persistent gRPC streams with thousands of agents, dispatches commands to them, bridges WebSocket connections from browsers for shell and desktop sessions, and forwards telemetry data to the metric service.

## Directory Structure

```text
agent-hub/
├── docs/                   # Detailed feature documentation
├── src/main/java/dev/pulsermm/agenthub/
│   ├── api/                # REST controllers and internal dispatch endpoints
│   │   └── internal/       # Internal APIs for command dispatching
│   ├── config/             # Spring configuration (WebSocket, gRPC, mTLS)
│   └── infrastructure/
│       ├── grpc/           # gRPC server, agent stream management, AgentRegistry
│       ├── ws/             # WebSocket handlers (shell, desktop signaling)
│       ├── desktop/        # Desktop session dispatching and lifecycle
│       ├── file/           # File transfer coordination
│       └── mtls/           # mTLS certificate validation
├── src/main/resources/
│   └── db/migration/       # (none - agent-hub is stateless)
├── Dockerfile              # Containerization definition
└── pom.xml                 # Maven dependencies
```

## Key Responsibilities

- **gRPC Control Plane** — Accepts and maintains long-lived bidirectional gRPC streams from agents, each authenticated via mTLS
- **Agent Registry** — In-memory registry mapping endpoint UUIDs to active agent streams for fast lookup and dispatch
- **Command Dispatch** — Receives commands from internal APIs and pushes them onto agent streams
- **WebSocket Bridges** — Proxies WebSocket messages from browser clients to agent gRPC streams (shell I/O, desktop signaling)
- **Session Lifecycle** — Manages desktop and shell session state, coordinates handshakes between browser and agent
- **File Transfer** — Coordinates file uploads and downloads between browser and agent via WebRTC data channels
- **Metric Forwarding** — Receives metric batches from agents and forwards them to metric-service

## Features & Internal Documentation

* **[gRPC Agent Streams](docs/grpc-streams.md)** - Explains how agents connect, authenticate, and maintain persistent bidirectional streams; the AgentRegistry in-memory storage.
* **[Command Dispatch](docs/command-dispatch.md)** - Details how script, software, process, and desktop commands are queued and pushed onto agent streams; internal dispatch API endpoints.
* **[WebSocket Bridges](docs/websocket-bridges.md)** - Covers the conversion layer between browser WebSocket (shell, desktop signaling) and agent gRPC streams.
* **[Session Management](docs/session-management.md)** - Explains desktop and shell session lifecycle, handshakes, cleanup, and orphaned session detection.
* **[File Transfer](docs/file-transfer.md)** - Documents how file uploads and downloads are coordinated via WebRTC data channels with path traversal protection.

## Networking

- **HTTP Server** — Listens on `localhost:8092` (configurable). Hosts WebSocket endpoints for shell and desktop signaling.
- **gRPC Server** — Listens on `0.0.0.0:9090` (configurable). Accepts agent connections over mTLS.
- **mTLS** — All agent gRPC connections require a valid certificate issued by ca-service. The certificate is checked against the revocation list on every connection.
- **Redis** — Uses Redis to track which hub instance owns each connected agent (for horizontal scaling in production).
- **RabbitMQ** — Listens to domain events (agent enrolled, session created) via AMQP for state synchronization.

## Architecture Notes

**Stateless control plane.** The agent-hub itself does not persist state to a database. All agent streams are held in memory in the AgentRegistry. For horizontal scaling in production, a Redis registry replaces the in-memory map, and agents may reconnect to any hub instance.

**mTLS every connection.** Agents authenticate via mutual TLS. The gRPC server validates the client certificate against the revocation list, rejecting revoked endpoints immediately.

**Dispatch by UUID.** Commands arrive on internal REST APIs keyed by endpoint UUID. The hub looks up the endpoint in its registry and pushes the command onto the agent's stream.

**WebSocket-to-gRPC bridging.** The browser opens a WebSocket for shell I/O or desktop signaling. The hub's WebSocket handler reads messages and translates them into gRPC events, sending them down the agent's stream.

## Related Services

- **[Endpoint Service](../endpoint-service/README.md)** — Manages agent enrollment, groups, tags, and initiates session requests (sends command to agent-hub)
- **[CA Service](../ca-service/README.md)** — Signs agent certificates and maintains revocation list (hub validates against it)
- **[Metric Service](../metric-service/README.md)** — Receives metric batches forwarded from agent-hub
- **[Commands Service](../commands-service/README.md)** — Creates script/software/process commands; agent-hub delivers them
- **[API Gateway](../api-gateway/README.md)** — Routes WebSocket requests for shell and desktop signaling to agent-hub
