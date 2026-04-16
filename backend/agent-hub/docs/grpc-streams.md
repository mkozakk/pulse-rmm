# gRPC Agent Streams (`infrastructure/grpc/`)

Manages the persistent bidirectional gRPC connection between each agent and the hub.

## Overview

Each agent establishes a single long-lived bidirectional gRPC stream to the hub on startup. This stream carries all downstream commands and upstream responses. The hub holds these streams in an in-memory registry (or Redis registry in production) keyed by endpoint UUID.

## Key Components

**AgentRegistry** (`AgentRegistry.java`)
- In-memory `Map<UUID, StreamObserver<GatewayCommand>>`
- When an agent connects, the hub calls `register(endpointId, observer)` to store the stream
- Unregisters when the stream closes (agent disconnects, revocation, etc.)
- Thread-safe via `ConcurrentHashMap` and `SynchronizedObserver` wrapper (prevents concurrent `onNext()` corruption)

**gRPC Server** (`AgentHubGrpcServer.java`)
- Listens on `0.0.0.0:9090` (configurable via `grpc.server.port`)
- Implements `ControlStreamService` from `.proto` definition
- Accepts `ControlStream(stream<AgentHello, GatewayCommand>) → stream<GatewayEvent>`

**mTLS Validation** (`MtlsInterceptor.java`)
- Every incoming gRPC connection is intercepted
- Client certificate is extracted and validated:
  - Certificate is not expired
  - Certificate is not revoked (checked against CA revocation list)
  - Subject DN contains the endpoint UUID (mTLS identity)
- Invalid certificates are rejected with `UNAUTHENTICATED` status

## Connection Flow

1. **Agent startup** — Agent reads its endpoint ID and mTLS cert from disk (`internal/store`)
2. **Connect** — Agent dials `PULSE_SERVER:9090` and initiates ControlStream RPC
3. **Handshake** — Agent sends `AgentHello` containing endpoint ID, version, hostname, OS, architecture
4. **Registration** — Hub verifies the mTLS cert matches the endpoint ID in AgentHello, calls `register()`
5. **Ready** — Stream is now active; agent and hub exchange messages for the lifetime of the connection
6. **Cleanup** — When the stream closes (network loss, revocation, agent shutdown), hub calls `unregister()`

## Stream Message Types

**Downstream (hub → agent):**
- `ScriptCommand` — Execute a shell script
- `SoftwareCommand` — Install/update/remove a package
- `ProcessCommand` — Kill a process
- `OpenShellSession` — Create a new shell session (PTY/ConPTY)
- `StartDesktopSession` — Begin screen capture and WebRTC negotiation
- `CloseSession` — Terminate a session (shell or desktop)

**Upstream (agent → hub):**
- `CommandAck` — Acknowledgement that command was received
- `CommandResult` — Exit code and output from a script/process
- `HeartbeatMessage` — "Agent is alive" signal
- `MetricsBatch` — CPU, memory, disk, network stats
- `ShellInput`/`ShellOutput` — Shell session I/O
- `DesktopCapture` — (Sent via WebRTC, not gRPC)

## Concurrency & Thread Safety

gRPC's `StreamObserver` is not thread-safe. Multiple threads (WebSocket handler, internal API dispatcher, metric forwarder) may call `onNext()` on the same agent's stream simultaneously.

**Solution:** `SynchronizedObserver` wraps the raw observer and synchronizes all calls to `onNext()`, `onError()`, and `onCompleted()` using a `ReentrantLock`.

This ensures that the underlying gRPC message framing is never corrupted by concurrent writes.

## Horizontal Scaling (Production)

In a single-hub deployment, agents reconnect to the same hub. In a multi-hub Kubernetes cluster:

- When an agent connects, the hub writes `{endpointId → hubInstanceId}` to Redis with a TTL
- When an API needs to dispatch a command, it queries Redis to find the owner hub
- If the endpoint is offline, the command is queued (handled by commands-service)
- If the hub crashes, agents reconnect to any available hub (via load-balanced endpoint)
- The Redis entry expires after the TTL, allowing re-registration to another hub

For local development, the Redis registry is disabled and the in-memory map is used.
