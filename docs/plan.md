# Pulse RMM - general purpose RMM service

## overview

pulse rmm is an enterprise system for managing a fleet of endpoints across an organization

## capabilities

with pulse-rmm, you can:
- monitor and supervise a fleet of endpoints across many metrics: ram, cpu, gpu, disk usage/info, os data (uptime, activity status)
- monitor and manage: processes, services, task scheduler, file system, and installed software via package manager (chocolatey, apt, dnf)
- access terminal of the endpoint
- install agent with single .exe / command / invitation link
- open a remote desktop session from webapp - not only screen, but also file, audio and mouse/keyboard transfer
- get notified when metrics cross thresholds
- os autoupdater
- third-party patch management (chrome, acrobat, java, etc.) via package manager wrappers
- scripting and automations across entire fleet
- policy engine - declarative desired-state config applied per group/tag (installed apps, services running, firewall rules)
- remote wipe for lost/stolen endpoints
- register a new technician - with permissions based on specified roles
- technician auth with mfa + sso (saml / oidc) for enterprise identity providers
- zero trust approach - every action must be authorized
- review audit logs - what was downloaded, when the endpoint was rebooted, who logged in, who accessed which feature
- access endpoint even when the user is logged out (daemon-registered)
- install agent by downloading and running .exe, copying link to .exe, or a one-liner script that automates all tasks
- for linux: one-liner for downloading and installing, with .deb and .rpm packages
- agent autoupdater
- rest api + outbound webhooks for integrating with customer itsm tools (jira, servicenow)

## scope & scale

### supported platforms (agent)
- windows 10+ on x86_64 (older windows versions are out of scope - modern go runtime no longer supports them)
- debian-based linux (.deb - ubuntu lts, debian)
- fedora-like linux (.rpm - fedora, rhel, rocky, alma)
- macos: out of scope (no dev access)

### supported browsers (client)
- latest stable chrome, firefox, edge, safari

### data retention
- metrics, audit records, and logs kept for 30 days, then pruned

### tenancy & multi-org
- single deployment = one fleet
- **fleet can be divided into multiple organizations** (multi-org support)
- each organization has isolated endpoint groups, users, roles, and permissions
- organization membership is managed via the RBAC service (keycloak)
- permissions are scoped per organization - a user in org A cannot access org B's endpoints
- typical use case: managed service provider (msp) running one pulse-rmm instance for multiple customers

## endpoint organisation

### groups
- every endpoint belongs to **exactly one** group
- groups form a **tree** (e.g. `hq > sales > laptops`); permission scoping inherits downward
- groups are the unit of **rbac scoping**: granting `remote:desktop:control` on `group:sales` covers every endpoint in `sales` and its descendants
- enrolment tokens are bound to a group - an endpoint enrolled with a given token lands in that group automatically
- admins can move endpoints between groups after enrolment

### tags
- free-form **key=value** labels (e.g. `env=prod`, `site=warsaw`, `role=dev-laptop`)
- **many-to-many** with endpoints (an endpoint can carry any number of tags)
- used for **ad-hoc filtering**, **alert rule targeting**, and **policy selectors** ("apply policy X where `env=prod`")
- three sources: installer parameters at enrolment, auto-tagging rules based on endpoint attributes (os, hostname pattern, ip range), manual assignment in the webapp

### endpoint identity
- at install the installer receives an invitation token scoped to a group
- on first run the agent generates an ed25519 keypair and is issued a stable **endpoint id** (uuid) by the backend during enrolment
- the id persists across reboots, os updates, and hostname changes; it is the primary key for all endpoint data

## technical components

### agent
- communicates with a backend service
- manages everything endpoint-related
- offline queue: buffers metrics and command acks locally when offline, resend on reconnect

### backend
- stores metrics, config, identity, audit
- middleman between agent and client
- issues signed commands to agents, receives acks and metric batches

### relay (stun/turn)
- responsible for p2p/indirect communication during remote access sessions

### client
- webapp exposing all system capabilities
- opens window with remote desktop view

## brief overview of technologies used

### agent
- go
- pure .exe for windows, .rpm and .deb for linux
- screen capturing: desktop duplication api on windows, xshm + xgetimage on x11, pipewire + xdg-desktop-portal on wayland (note: wayland portal requires an interactive consent prompt per capture session, so **unattended access is not supported on wayland** - an endpoint running a wayland session must have a logged-in user to approve capture)
- video codec: h264 via hardware encoder on windows (d3d11 / nvenc / quicksync / amf); on linux hardware encode via vaapi / nvenc / amf is preferred, software fallback is vp9 (royalty-free - avoids the h264 patent/licensing problem on linux, which would otherwise force either gpl-viral x264, cisco's openh264 binary, or a paid mpeg la license)
- codec negotiation on linux (sdp offer ordering, most preferred first):
  1. av1 hardware encode - if gpu supports it (intel arc, nvidia 40-series, amd rx 7000). best compression, royalty-free
  2. h264 hardware encode (vaapi / nvenc / amf) - license is paid by the gpu vendor, lowest latency, widest hw support
  3. vp9 hardware encode (vaapi on intel 10th gen+ igpu, newer amd/nvidia) - royalty-free middle ground
  4. vp9 software (libvpx-vp9) - always-available fallback, royalty-free, real-time-capable on any modern cpu
- explicitly excluded from the offer on linux: software h264 (licensing) and software av1 (too slow for real-time remote desktop)
- the agent probes available encoders at startup and advertises only the ones it can actually run; webrtc sdp offer/answer picks the best match supported by both agent and client
- audio capture: wasapi on windows, pipewire / pulseaudio on linux
- input injection: sendinput on windows, xtest on x11, libevdev virtual devices on wayland
- goroutines for multithreaded jobs
- batches metrics before sending via grpc
- ed25519 for authenticating and executing signed commands; agent generates the keypair locally on first run and registers the public key with the backend during enrolment. backend can invalidate a key and require re-enrolment to rotate
- secret distribution: script parameters / credentials delivered sealed to the agent, decrypted only in-process

### transport split
- **control plane (grpc)**: enrollment, metric batches, policy pull, command delivery + ack, heartbeat, webrtc signaling
- **remote session (webrtc)**: video track, audio track, input data channel, file transfer data channel - all tunneled through the relay (stun/turn) when direct p2p isn't available

### infra
- cloud native
- podman for containers
- podman compose for local dev and small environments
- postgres for services
- timescaledb extension for metric time-series at scale
- redis for hot state + agent connection registry (so any control-plane pod can route commands to the pod holding an agent's stream)
- microservices with stateless control plane; sticky session on the lb only for keeping agent grpc streams pinned during their lifetime
- test environment
- load balancing with sticky session for streams
- stun/turn (relay)
- separate ingress paths for webapp/api traffic vs agent grpc traffic
- mtls everywhere between services and to agents
- s3-compatible storage for installer binaries and autoupdate artifacts
- prometheus / grafana for observability
- terraform for infra provisioning
- secrets manager
- rate limiting
- api gateway

### backend
- microservices
- java 21
- virtual threads
- spring boot
- multi-module maven
- flyway for schema migrations
- grpc-java + protobuf / buf for agent-backend communication
- spring web (mvc) for client-facing rest api
- springdoc-openapi for api docs
- spring security + oauth2 resource server for authn / authz
- spring data jpa / hibernate for db access
- hikaricp for connection pooling
- lettuce for redis
- jackson for json serialization
- jakarta bean validation for input validation
- rabbitmq for inter-service messaging and webhook dead-letter queue
- resilience4j for retries and circuit breakers (outbound webhooks, flaky deps)
- micrometer → prometheus for metrics
- logback with json encoder for structured logs → loki
- spring @scheduled for background jobs (canary rollout, policy reconciliation, token expiry)
- aws sdk v2 / minio client for s3-compatible storage
- bucket4j for in-app rate limiting
- junit 5 + testcontainers + mockito + wiremock for testing

### client (webapp)
- react
- vite
- redux
- webrtc api
