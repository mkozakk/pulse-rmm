# Pulse RMM Agent

## Directory Structure

```text
agent/
├── desktop/                # Handles Remote Desktop (WebRTC, Video capture, Mouse/Keyboard inputs)
├── docs/                   # Full documentation of all agent features
├── gen/                    # Auto-generated Protobuf files (gRPC communication rules)
├── internal/
│   ├── control/            # Manages the long-lived bi-directional gRPC stream to the server
│   ├── enrolment/          # Responsible for the first-time setup (registering the agent)
│   ├── metrics/            # Gathers CPU, RAM, Disk stats and sends Heartbeats
│   ├── script/             # Executes raw shell scripts pushed by the backend
│   ├── shell/              # Handles Remote Terminal features (pty, piping inputs/outputs)
│   ├── software/           # Scans installed software on the OS and runs package managers
│   └── store/              # Handles saving/reading the endpoint ID and private keys to disk
├── main.go                 # The entry point of the app that wires everything together
└── go.mod / go.sum         # Go dependency files
```

## Features & Internal Documentation

* **[Core Initialization (main.go)](docs/core.md)** - How the agent starts up and orchestrates its background jobs.
* **[Local Storage & Crypto](docs/store.md)** - How the agent persists its identity (Ed25519 Keys and UUID) across reboots.
* **[Enrolment Flow](docs/enrolment.md)** - How the agent introduces itself to the server for the first time.
* **[gRPC Control Stream](docs/control.md)** - The 24/7 bidirectional phone-line used for remote command delivery.
* **[Metrics & Heartbeat](docs/metrics.md)** - Hardware telemetry gathering (`gopsutil`).
* **[Script Execution](docs/script.md)** - Running arbitrary `bash`/`powershell` blocks on the host.
* **[Remote Terminal (Shell)](docs/shell.md)** - Proxied pseudo-terminal (PTY) for live browser-based CLI access.
* **[Software Management](docs/software.md)** - Scanning and installing packages via Chocolatey/APT.
* **[Remote Desktop (WebRTC)](docs/desktop.md)** - Screen capture and mouse/keyboard injection powered by Pion WebRTC.

## Flow of Requests / Sessions

The Agent does not listen for incoming HTTP connections (due to NATs/Firewalls). It initiates all connections outbound.

### 1. Startup
1. Agent boots up (`main.go`).
2. Checks `/var/lib/pulse-agent/` for an ID and Key (`internal/store`).
3. If missing, makes a gRPC `Enrol` call to the Gateway (`internal/enrolment`).
4. Once enrolled, fires up background Goroutines (Heartbeats, Metrics, Software Scanning).
5. Finally, opens the Control Stream (`internal/control`), which blocks the main thread forever.

### 2. Command Delivery Flow
When an admin clicks "Run Script" in the web UI:
1. The backend pushes a `GatewayCommand` down the open Control Stream.
2. The agent reads the command from its `inCh` channel and passes it to `dispatchCmd()`.
3. `dispatchCmd()` looks at the payload type (`ScriptCommand`, `SoftwareCommand`, `OpenShell`, etc.).
4. The corresponding module (`internal/script`, `internal/software`) executes the logic locally.
5. The module drops the result (e.g. "Exit Code 0, Output: Hello") into the `outCh` channel.
6. The Control Stream picks up the event from `outCh` and pushes it back up to the server.

### 3. Remote Desktop
1. Backend sends a `StartDesktopSession` command over the Control Stream.
2. The agent's `desktop.Handler` intercepts it.
3. The agent and the browser begin exchanging SDP and ICE Candidates (WebRTC connection data) via the Control Stream.
4. Once the WebRTC peer connection is established (often via STUN/TURN relays), a direct UDP-based media stream begins.
5. The agent continuously reads the OS screen buffer, encodes it to H.264, and streams the video track.
6. Mouse clicks from the browser come through a WebRTC DataChannel, which the agent converts into fake OS-level inputs.
