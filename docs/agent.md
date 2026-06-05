# The agent

The agent is the piece of Pulse RMM that actually runs on a managed machine. It is a single Go binary with no runtime dependencies, installed as a system service so it starts on boot and runs whether or not anyone is logged in. Everything a technician does to an endpoint — reading its metrics, opening a shell, controlling its desktop, installing software, running a script — is carried out by this binary on the far side of one connection. This document describes how it is structured and how it behaves at runtime. For building and packaging it, see [agent/README.md](../agent/README.md); for the per-module internals, the references at the end of this page go straight to the source.

## Why the agent dials out

The defining constraint of the agent is that it never listens for incoming connections. Endpoints sit behind NAT and corporate firewalls, and asking an administrator to open inbound ports on every managed machine is a non-starter. So the agent inverts the usual direction: it reaches out to the backend and holds a long-lived, bidirectional gRPC stream open, and the backend pushes commands down that stream as they arise. The stream is the agent's lifeline — it is how the backend reaches an otherwise unreachable machine — and most of the agent's design follows from keeping it healthy.

## Startup and enrolment

When the binary starts it reads its configuration (the backend URL, the data directory, the log level, and on a fresh install an enrolment token) and then looks on disk for an existing identity. An enrolled agent has two things saved locally: its ed25519 private key and the stable endpoint UUID the backend issued it. If both are present the agent skips straight to connecting. If they are missing it assumes this is a first run and enrols.

Enrolment happens exactly once in an agent's life. The agent generates an ed25519 keypair, builds a certificate-signing request, and presents the enrolment token together with its public key, host details, and the CSR to the backend. The backend validates the token, has the certificate authority sign the CSR, and returns the endpoint UUID, the signed certificate, and the CA bundle. The agent writes all of that to disk and is now a first-class member of the fleet. Because the certificate is issued here, the agent finishes enrolment already holding everything it needs to open the mutually-authenticated control stream — there is no second bootstrap. Enrolment is idempotent: an agent that retries with the same key gets the same UUID rather than creating a duplicate endpoint.

With an identity in hand the agent starts its recurring background work as separate goroutines, each a simple loop driven by a `time.Ticker` — one sends heartbeats, one collects and pushes metrics, one rescans installed software — and then enters the control-stream loop, which blocks the main thread for the rest of the process's life.

## The control stream and command dispatch

The control stream is a single bidirectional gRPC connection to the backend's agent hub. After dialing, the agent sends a hello message identifying itself, and the connection settles into a steady rhythm built on Go channels. A dedicated goroutine does nothing but call `Recv` on the stream and drop whatever arrives onto an inbox channel; the main loop selects on an outbox channel and calls `Send` whenever one of the agent's subsystems has produced a result. This decoupling is what lets the agent handle a command and report an unrelated result at the same time without either blocking the other.

A command pulled off the inbox is handed to a dispatcher that switches on the protobuf payload type and calls the matching module: a script command goes to the script executor, a software command to the software manager, a shell or desktop request to those subsystems, and so on. Each module does its work locally and pushes its result onto the outbox, where the stream loop picks it up and sends it back. This is the command-delivery pattern, and it is the same shape for every endpoint-facing feature — the modules differ, the path does not.

## Metrics and heartbeat

On its timer the agent gathers CPU, memory, disk, and uptime figures with gopsutil, batches them, and pushes the batch up the stream. A separate, lighter heartbeat tells the backend the agent is still alive; if those stop arriving for around ninety seconds the backend marks the endpoint offline. Batching keeps the steady-state chatter low even across a large fleet.

## Remote shell

A shell request asks the agent to spawn a real pseudo-terminal and proxy it. On Linux that is bash behind a PTY; on Windows it is PowerShell or cmd behind ConPTY. A session manager tracks the open terminals in a map guarded by a mutex, because several technicians may open tabs against the same endpoint at once, and each session gets a goroutine that continuously reads the terminal's output and forwards it up the stream. Keystrokes coming back down are routed to the right session by id and written straight into the terminal's input. Closing the tab closes the session and releases the process and its goroutine, with no orphan left behind.

## Remote desktop

Remote desktop is the agent's most involved capability and the system's headline feature.

![Remote endpoint features](media/remote-endpoint-features.gif)

When a desktop session starts, the agent creates a Pion WebRTC peer connection configured with STUN and TURN servers and immediately begins capturing the screen. Capture is done by spawning ffmpeg against the platform's display API — `gdigrab` on Windows, `x11grab` or a PipeWire portal on Linux — and reading the encoded H.264 stream back from ffmpeg's output. The agent assembles those bytes into complete frames and writes them to a Pion video track at thirty frames a second; audio is captured alongside. The browser, once it has been told the session is ready, sends a WebRTC offer, which is relayed to the agent through the control stream; the agent sets the remote description, flushes any ICE candidates that arrived early, and answers. Candidates trickle both ways throughout, and when no direct path can be found the media is relayed through the TURN server.

Input and files travel over their own WebRTC data channels rather than the video track. Mouse and keyboard events arrive on an `input` channel and are injected into the operating system natively — `SendInput` on Windows, a `uinput` virtual device on Linux — rate-limited to sixty events a second so a burst cannot overwhelm the host. File transfer uses a separate channel so a large copy does not stall input; uploads are written to a scratch directory and downloads are validated to stay within the user's home directory, closing off path-traversal attempts.

Two operational details are worth knowing. Session teardown can be triggered either by the browser ending the session or by the peer connection dropping, and both paths converge on a single guarded close so the session is never torn down twice. And because the start and end requests can race through the asynchronous bridge between browser and agent, the agent remembers session ids that were ended before they were ever started, and refuses a late start for one of them — otherwise a stale start could kill the session that is actually live. On Linux the capture often runs in a helper process under the logged-in user's session rather than as the service account, since that is where the user's display lives; Wayland additionally requires the user to approve each capture through the portal, which is why unattended desktop access is not supported there.

## Software and processes

The agent scans installed software using whatever the platform offers — the Windows registry, and `dpkg`, `dnf`, or `flatpak` on Linux — and reports the inventory on a timer and on demand. Software commands (install, update, remove) are executed against the native package manager and acknowledged with their output. Process commands let a technician list what is running on the endpoint and terminate a chosen process. Both arrive and report through the ordinary command-delivery path.

## Scripts and secrets

A script command carries a body to run in a subprocess — PowerShell on Windows, bash on Linux — whose combined output and exit code are captured and returned. Any secrets the script needs are delivered sealed and only decrypted inside the agent process, so they never touch disk in the clear or appear in the result that flows back to the audit log.

## Auto-update and rollback

The agent keeps itself current without anyone touching the endpoint. On an interval it asks the backend whether a newer version exists; if one does, it downloads the binary, verifies its SHA-256, and swaps it in. On Linux the swap is an atomic rename and systemd restarts the new binary. On Windows the running executable cannot be replaced in place, so the swap is queued as a pending move that the OS performs on the next reboot. Either way the new binary is on probation: a pending-update marker is written before the swap, and on the next start the agent waits for the first successful heartbeat to confirm the new version is healthy. If that heartbeat does not come within the timeout, the agent restores the previous binary and restarts on the known-good version. A bad release can therefore fail a single endpoint temporarily but cannot brick it.

## Identity, security, and local state

The agent's identity is its ed25519 key and the mTLS certificate signed at enrolment, both stored in its data directory along with the endpoint UUID. The control stream is mutually authenticated, and the backend checks the agent's certificate and its revocation status on every connection, so revoking an endpoint immediately stops it from connecting and from renewing its certificate. The agent renews its certificate ahead of expiry through the same internal endpoint it used at enrolment.

## The endpoint user

On a machine with a logged-in graphical session the agent also presents a small system-tray indicator. It shows whether the agent is running and whether a technician is currently connected, and it offers a "request help" button that raises an in-app notification to technicians with the endpoint and an optional message. On headless machines — Linux servers, or Windows sessions with no desktop — the tray is simply absent and the agent logs that and carries on; technicians interact with those endpoints through the webapp only.

## Deeper reference

This page is the narrative overview. The agent's own [README](../agent/README.md) covers the directory layout, build targets, packaging into `.deb`/`.rpm`/`.exe`, and manual install steps. Each subsystem has a focused document under [agent/docs/](../agent/docs/): [core](../agent/docs/core.md), [config](../agent/docs/config.md), [store](../agent/docs/store.md), [enrolment](../agent/docs/enrolment.md), [service lifecycle](../agent/docs/svc.md), [control stream](../agent/docs/control.md), [metrics](../agent/docs/metrics.md), [script](../agent/docs/script.md), [shell](../agent/docs/shell.md), [software](../agent/docs/software.md), [remote desktop](../agent/docs/desktop.md), and [auto-update](../agent/docs/update.md).
