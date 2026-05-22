# Configuration (`internal/config/`)

Manages loading and parsing the agent's runtime configuration from YAML.

### code

[`config.go`](../internal/config/config.go):
	- `Config` - Data structure holding API gateway address, gRPC address, enrolment token, data directory, and log level.
	- `DefaultPath()` - Determines the platform-specific config file location (`/etc/pulse-agent/config.yaml` on Linux, `C:\ProgramData\pulse-agent\config.yaml` on Windows, or `PULSE_CONFIG` env var).
	- `Load(path)` - Reads and parses YAML configuration from disk. Validates that `api_url` is present.
	- `RemoveToken()` - Removes the enrolment token from config after successful first-time enrollment (prevents re-enrollment on restart).

### description

The agent requires a configuration file to know where to contact the backend, how to authenticate on first boot, and where to store its identity. This module abstracts all configuration parsing logic. On startup, `main.go` calls `config.Load()` with the path determined by `DefaultPath()`, which respects the `PULSE_CONFIG` environment variable for test flexibility or looks in platform-specific secure directories. The configuration is YAML-formatted and contains:

- `api_url`: The address of the backend API gateway (e.g., `http://localhost:8080`). Required; the Load function will error if missing.
- `grpc_addr`: The address of the gRPC server for control stream, metrics, and software updates (e.g., `localhost:9090`). If omitted, defaults to `localhost:9090` in `main.go`.
- `enrolment_token`: A one-time UUID token provided by an administrator to bootstrap the agent's identity. Only required on first boot; if the agent has already enrolled (persisted ID exists), this field is ignored.
- `data_dir`: Directory where the agent stores its persistent identity (keys, endpoint ID) and logs (e.g., `/var/lib/pulse-agent`, `C:\ProgramData\pulse-agent`).
- `log_level`: Controls verbosity of agent logs (`debug`, `info`, `warn`, `error`).

After successful enrolment, the `RemoveToken()` method is called to clear the token from the config file, ensuring that if the config is later read by mistake, it cannot be used for re-enrollment.
