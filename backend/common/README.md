# Common Module (`backend/common/`)

The `common` module acts as a shared library for the entire backend ecosystem. It does not run as an independent service.

## Directory Structure
```text
common/
├── src/
│   ├── generated/      # Automatically generated Java classes from Protobuf definitions
│   └── main/java/      # Shared utilities, exceptions, and domain records (if any)
└── pom.xml             # Maven configuration and gRPC/Protobuf build plugins
```

## Features & Internal Documentation

* **[Generated Protobuf Stubs](docs/proto.md)** - Explains how the shared API contracts are compiled and utilized across services.

## Role in the System
In a microservices architecture, sharing code can lead to tight coupling. However, sharing **API contracts** (like Protobuf definitions) and **standardized error handling** is considered best practice. This module guarantees that when the Gateway talks to the Metric Service, or when an Agent talks to the Gateway, they both use the exact same strongly-typed Java classes to serialize and deserialize their data.
