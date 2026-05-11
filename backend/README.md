# Pulse RMM Backend

This directory contains the Java 21 Spring Boot 3 modular monolith (or microservices network) that forms the server-side infrastructure for Pulse RMM.

## Architecture
The backend is designed around a multi-module Maven build. Services communicate synchronously via HTTP REST or gRPC (via the API Gateway) and asynchronously via RabbitMQ domain events.

## Components
* **[Common](common/README.md)** - Shared utilities, generated Protobuf classes, and domain records used across services.
* **[API Gateway](api-gateway/README.md)** - The single entry point for all frontend REST traffic, WebSocket proxies, and agent gRPC control streams. Handles authentication and authorization.
* **[Identity Service](identity-service/README.md)** - Handles JWT authentication, Users, Roles, and Role-Based Access Control (RBAC).
* **[Enrolment Service](enrolment-service/README.md)** - Manages endpoint registration, cryptographic key validation, and UUID issuance.
* **[Metric Service](metric-service/README.md)** - Ingests hardware telemetry and heartbeats from agents into TimescaleDB.
* **[Script Service](script-service/README.md)** - Manages the script library and orchestrates remote execution commands.
* **[Software Service](software-service/README.md)** - Tracks installed software inventory and dispatches package management commands.
* **[Remote Service](remote-service/README.md)** - Provides necessary backend components for shell and desktop remote control.
