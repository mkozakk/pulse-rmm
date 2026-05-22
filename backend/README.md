# Pulse RMM Backend

This directory contains the Java 21 Spring Boot 3 modular monolith (or microservices network) that forms the server-side infrastructure for Pulse RMM.

## Architecture
The backend is designed around a multi-module Maven build. Services communicate synchronously via HTTP REST or gRPC (via the API Gateway) and asynchronously via RabbitMQ domain events.

## Components

### Core Infrastructure
* **[Common](common/README.md)** - Shared utilities, generated Protobuf classes, and domain records used across services.
* **[API Gateway](api-gateway/README.md)** - The single entry point for all frontend REST traffic, WebSocket proxies, and agent gRPC control streams. Handles authentication and authorization.
* **[Identity Service](identity-service/README.md)** - Handles JWT authentication, Users, Roles, and Role-Based Access Control (RBAC).

### Endpoint Management
* **[Endpoint Service](endpoint-service/README.md)** - Agent enrollment, group management, tag system, agent version distribution, and remote session coordination.

### Observability & Alerting
* **[Metric Service](metric-service/README.md)** - Ingests hardware telemetry and heartbeats from agents into TimescaleDB.
* **[Alert Service](alert-service/README.md)** - Threshold-based alerting rules on metrics with real-time notifications.
* **[Audit Service](audit-service/README.md)** - Immutable audit log of all system actions for compliance.

### Operations & Commands
* **[Commands Service](commands-service/README.md)** - Manages script library, software inventory, and command execution across the endpoint fleet.

### Integration & Notifications
* **[Integration Service](integration-service/README.md)** - Manages webhooks and outbound integrations with external systems.
* **[Notification Service](notification-service/README.md)** - Delivers real-time in-app notifications to technicians via Server-Sent Events.
