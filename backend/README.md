# Pulse RMM Backend

This directory contains the Java 21 Spring Boot 3 modular monolith (or microservices network) that forms the server-side infrastructure for Pulse RMM.

## Architecture
The backend is designed around a multi-module Maven build. Services communicate synchronously via HTTP REST or gRPC (via the API Gateway) and asynchronously via RabbitMQ domain events.

## Components

### Core Infrastructure
* **[Common](common/README.md)** - Shared utilities, generated Protobuf classes, and domain records used across services.
* **[API Gateway](api-gateway/README.md)** - The single entry point for all frontend REST traffic and WebSocket proxies. Handles authentication and authorization, then routes to the owning service.
* **[RBAC Service](rbac-service/README.md)** - Identity and access control: JWT, users, roles, the permission model, Keycloak OIDC integration, and multi-organization scoping.

### Endpoint Connectivity
* **[Agent Hub](agent-hub/README.md)** - The agent control plane. Holds each agent's long-lived gRPC stream over mutual TLS, dispatches script/software/process/desktop commands onto it, bridges the shell and desktop-signaling WebSockets, and forwards metric batches.
* **[CA Service](ca-service/README.md)** - The internal certificate authority. Signs the certificate-signing requests submitted at enrolment and renewal, and maintains the revocation list used to authenticate agents.
* **[Endpoint Service](endpoint-service/README.md)** - Agent enrolment, group management, the tag system, agent-version distribution, and remote-session coordination.

### Observability & Alerting
* **[Metric Service](metric-service/README.md)** - Ingests hardware telemetry and heartbeats from agents into TimescaleDB.
* **[Alert Service](alert-service/README.md)** - Threshold-based alerting rules on metrics, with real-time in-app notifications delivered to technicians over Server-Sent Events.
* **[Audit Service](audit-service/README.md)** - Immutable audit log of all system actions for compliance.

### Operations & Commands
* **[Commands Service](commands-service/README.md)** - Script library and execution, software inventory and package operations, and remote process control across the endpoint fleet.

### Integration
* **[Integration Service](integration-service/README.md)** - Outbound, HMAC-signed webhooks to external systems, with retries and a dead-letter queue.
