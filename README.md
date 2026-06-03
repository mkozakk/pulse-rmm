# Pulse RMM

Enterprise remote management system for Windows and Linux endpoints. Deploy lightweight agents, monitor real-time metrics, execute remote shell and desktop sessions, manage software lifecycle, enforce policies, and audit all actions across your fleet.

## What is Pulse RMM?

Pulse RMM is a complete RMM platform built from scratch for simplicity and clarity. It lets you:
- **See** what's happening on any endpoint in real time
- **Access** endpoints remotely via shell, desktop, or file transfer
- **Manage** software, scripts, and policies across the fleet
- **Know** who did what, when, and on which endpoints (immutable audit trail)
- **Control** who can do what via fine-grained permissions and roles

Think of it as a lightweight alternative to commercial platforms like ConnectWise or Kaseya, with a focus on core capabilities and operational transparency.

## Features

### Monitoring & Visibility

- **Live Metrics Dashboard** - Real-time CPU, RAM, disk, process counts, network I/O; charts update every 30 seconds
- **Historical Metrics** - Query 30 days of metric history by endpoint, date range, and metric type
- **Offline Detection** - Endpoints marked offline after 3 missed heartbeats (~90 seconds)
- **System Inventory** - Hostname, OS version, architecture, CPU model, core count, RAM capacity, uptime
- **Process Monitoring** - List running processes with memory/CPU usage; kill hung or rogue processes
- **Software Inventory** - Automated scanning of installed packages (Windows Registry, dpkg, dnf, flatpak)

### Remote Access & Control

- **Remote Shell** - Full PTY terminal on any online endpoint
  - Bash/sh on Linux, PowerShell/cmd on Windows
  - Full interactivity (pipes, redirects, color output)
  - Multiple concurrent sessions per endpoint
  - Timeout enforcement to prevent hung shells
  
- **Remote Desktop** - Live video streaming with input control
  - Desktop capture via H.264 codec (hardware-accelerated where available)
  - Full mouse and keyboard input injection
  - VP9 software fallback on Linux (royalty-free)
  - Works behind NAT via TURN relay (coturn)
  - File transfer over WebRTC data channel (drag-and-drop)
  - View-only mode available (no input injection)
  
- **File Transfer** - Upload/download files during remote sessions
  - Bi-directional transfer over encrypted WebRTC data channel
  - Chunked protocol for reliability

### Software & Package Management

- **Package Scanning** - Automated discovery of installed software
  - Windows: Registry-based scanning
  - Linux: dpkg, dnf, flatpak package detection
  
- **Remote Install/Update/Remove** - Execute package manager commands
  - Windows: winget (Windows 10+)
  - Linux: apt, dnf, flatpak
  - Bulk operations across multiple endpoints
  
- **OS Patch Management** - Schedule and deploy OS security updates
  - Third-party patch support (Chrome, Java, etc.)
  - Maintenance window scheduling
  - Rollback on failed updates

### Scripting & Automation

- **Script Library** - Create, organize, and share scripts
  - PowerShell and Bash support
  - Version control and edit history
  
- **Approval Workflow** - Enforce change control
  - Junior techs can only run approved scripts
  - Senior techs can create and approve scripts
  - Audit trail of every execution
  
- **Bulk Execution** - Run any script across 1 or 1000 endpoints
  - Real-time progress tracking
  - Per-endpoint output and exit codes
  - Timeout enforcement
  
- **Secret Injection** - Pass credentials securely
  - Encrypted at rest; decrypted only in agent process
  - Never logged or exposed in audit trail
  - Scoped to specific scripts and users

### Policy Engine

- **Declarative Policies** - Define desired state in YAML
  - Required applications and versions
  - Required services and their state
  - Firewall rules and settings
  
- **Drift Detection** - Continuous compliance checking
  - Compare actual vs desired state
  - Per-endpoint reasons for non-compliance
  - Scheduled evaluation every 30 seconds
  
- **Auto-Reconciliation** - Automatically fix drift
  - Generate install/start/configure commands
  - Execute across scoped groups or tags
  - Track compliance history

### Alerting & Notifications

- **Threshold Rules** - Define alerts based on metrics
  - CPU, memory, disk thresholds
  - Condition must hold for configurable duration (avoid noise)
  - Scoped to groups or tag selectors
  
- **Real-time Notifications** - SSE-based in-app alerts
  - Browser notification when threshold triggers
  - Alerts appear in-app within 30 seconds
  - One-click acknowledge with history
  
- **Webhook Integration** - Forward events to external systems
  - HMAC-SHA256 signed payloads (GitHub-style)
  - Automatic retry with exponential backoff
  - Dead-letter queue for failed deliveries
  - Integration with Jira, ServiceNow, custom tools

### Identity, Access Control & Multi-Tenancy

- **Permission-Based Access Model** - Fine-grained control
  - 20+ permissions covering all actions
  - Scope permissions to endpoint groups (hierarchy-aware)
  - Scope permissions to organizations (for MSPs)
  
- **Built-in Roles** - Pre-configured for common job functions
  - Admin (full access)
  - Senior Technician (all remote access, scripting, approvals)
  - Junior Technician (read-only shell and desktop view, run approved scripts)
  - Auditor (audit logs and read-only visibility)
  
- **Custom Roles** - Build roles from permission catalog
  - Clone and edit existing roles
  - Add/remove individual permissions
  
- **Direct Permission Grants** - Temporary elevated access
  - Grant one-off permissions to users
  - Time-bound (expires automatically)
  - Group-scoped or organization-scoped
  
- **Single Sign-On & MFA** - Enterprise authentication
  - OIDC integration (test with Keycloak)
  - TOTP-based multi-factor authentication
  - User disabling in IdP revokes access automatically
  
- **Multi-Organization Support** - Managed Service Provider ready
  - One deployment, multiple customer orgs
  - Complete org isolation (users, endpoints, policies)
  - Per-org RBAC and permissions

### Audit & Compliance

- **Immutable Audit Log** - Append-only record of all actions
  - Who, what, when, on which endpoint
  - Which permission was used for each action
  - No delete capability (tamper-proof)
  
- **Streaming Export** - Extract logs for compliance
  - CSV and JSON export formats
  - Date range and user filtering
  - Memory-efficient streaming (handles millions of records)
  
- **Action Categories Logged**
  - All API calls (REST endpoints)
  - All RPC calls (gRPC endpoints)
  - Script execution with parameters and output
  - Policy changes and drift reconciliation
  - User login, logout, privilege grants
  - File transfers and shell sessions
  - Software install/remove operations

### Enrolment & Agent Deployment

- **One-Time Enrollment Tokens** - Secure agent registration
  - Token scoped to specific endpoint groups
  - Tokens expire automatically (24 hours default)
  - One-time use (no token replay attacks)
  
- **Multiple Install Methods**
  - `.exe` installer (Windows)
  - `.deb` package (Ubuntu/Debian)
  - `.rpm` package (Fedora/RHEL)
  - One-liner bash/PowerShell commands
  - Signed packages for secure distribution
  
- **Automatic Service Installation**
  - systemd service on Linux
  - Windows SCM service on Windows
  - Auto-start on boot

### Agent Auto-Update & Rollback

- **Canary Rollout** - Phased deployment with safety
  - 1% of fleet gets new version first
  - Admin must approve advance to 10%, then 100%
  - Manual override available for urgent updates
  
- **Atomic Updates** - No partial or corrupt binaries
  - Download and verify SHA256 hash
  - Backup old binary before swap
  - Atomic rename (old → backup, new → active)
  
- **Automatic Rollback** - Fail-safe mechanism
  - Health check post-update (can start and reach backend?)
  - If heartbeat fails within 60s, auto-restore old binary
  - User notified of rollback event
  
- **Version Management** - Track and control agent versions
  - View active version per endpoint
  - See rollout progress
  - Mark version as stable or deprecated

### Endpoint Organization & Tagging

- **Group Hierarchy** - Tree-based organization
  - Create nested groups (e.g., `HQ > Sales > Laptops`)
  - Max depth limit (5 levels) to prevent complexity
  - Permissions inherit down the tree
  
- **Flexible Tagging** - Ad-hoc classification
  - Free-form key=value tags (e.g., `env=prod`, `site=warsaw`)
  - Many-to-many with endpoints
  - Tag filters on dashboards, alerts, and policies
  
- **Auto-Tagging Rules** - Automatic classification
  - Rule-based tag assignment at enrollment
  - Examples: OS detection, hostname pattern, IP range
  - Re-evaluate on-demand

### REST API & Automation

- **Complete REST API** - All UI operations via API
  - Authenticated with API keys (scoped to permissions)
  - Same permission model as web UI
  - OpenAPI documentation available
  
- **Webhook Callbacks** - Event-driven integrations
  - Alert fired, agent offline, session opened events
  - Signed with HMAC-SHA256
  - Automatic retry with dead-letter queue

### Remote Wipe & Decommission

- **Secure Decommissioning** - Destroy endpoint data
  - Two-person approval required (prevent accidents)
  - Wipe status tracking (pending/in-progress/completed)
  - Cross-platform support (Windows and Linux)

## Quick Start

### Prerequisites

- podman (or docker)
- Go 1.22+ (to build agent)
- Java 21 (to build backend)
- Node.js 18+ (to build webapp)
- make

### 1. Start the Local Dev Stack

```bash
# Copy environment template
cp deploy/.env.example deploy/.env

# Start all services (postgres, redis, rabbitmq, minio, backend, webapp)
podman compose -f deploy/compose.yaml up -d

# Wait for services to be healthy (~30s)
podman compose -f deploy/compose.yaml logs -f api-gateway
```

The webapp is available at `http://localhost:5173` (React dev server).

### 2. Log In

Default credentials (from `.env`):
- **Username:** `admin`
- **Password:** `admin`

### 3. Explore

- **Endpoints:** List of managed machines (empty until you enrol an agent)
- **Metrics:** CPU, RAM, disk charts (live + historical)
- **Shell:** Remote terminal access
- **Desktop:** Remote desktop with mouse/keyboard/file transfer
- **Scripts:** Upload and execute scripts on endpoints
- **Alerts:** Create threshold rules
- **Audit:** View all actions taken

## Architecture

Pulse RMM has four tiers:

```
Browser (Technician)
    ↓ HTTPS REST / WebSocket
API Gateway (Spring Boot)
    ↓ gRPC
Backend Microservices (Java 21)
    ↓
PostgreSQL + TimescaleDB + Redis + RabbitMQ + MinIO
```

Endpoints run a lightweight Go agent that:
- Collects metrics every 30s
- Executes commands pushed from the backend
- Streams desktop via WebRTC
- Stores identity (ed25519 keypair + X.509 cert) locally

See `docs/architecture.md` for detailed component diagrams and data flows.

## Build & Run

### Backend (Java)

```bash
cd backend
mvn verify          # Build + test all modules
mvn -pl api-gateway verify  # Build + test one service
```

### Agent (Go)

```bash
cd agent
go build ./...      # Build agent binary
go test ./...       # Run tests
```

### Webapp (React + Vite)

```bash
cd webapp
npm install
npm run dev         # Dev server (http://localhost:5173)
npm run build       # Production build → dist/
npm run test        # Run unit tests
```

## Testing

Run tests before committing:

```bash
# Backend unit + integration tests
cd backend && mvn verify

# Agent tests
cd agent && go test ./...

# Webapp tests
cd webapp && npm run test -- --run

# Full E2E suite (optional, takes longer)
make e2e
```

See `docs/testing.md` for detailed testing guide.

## Documentation

- **[Architecture](docs/architecture.md)** - System design, components, data flows
- **[Development Plan](docs/dev-plan.md)** - Sprint-by-sprint feature roadmap (all sprints complete)
- **[User Stories](docs/user-stories.md)** - Permissions catalog, roles, features by epic
- **[Testing Guide](docs/testing.md)** - How to run unit, integration, and E2E tests
- **[Agent Architecture](docs/agent.md)** - Agent components, startup flow, troubleshooting
- **[Webapp Architecture](docs/webapp.md)** - React app structure, state management, features
- **[Capabilities & Scope](docs/plan.md)** - Supported platforms, tenancy, endpoint organization

## Repository Structure

```
pulse-rmm/
├── agent/                 # Go agent (Windows/Linux)
├── backend/               # Java microservices (Maven multi-module)
│   ├── pom.xml
│   ├── api-gateway/
│   ├── rbac-service/
│   ├── endpoint-service/
│   ├── metric-service/
│   ├── alert-service/
│   ├── audit-service/
│   ├── integration-service/
│   ├── commands-service/
│   └── common/            # Shared protobuf stubs, exceptions
├── webapp/                # React + Vite (TypeScript)
├── proto/                 # Protocol buffers (gRPC contracts)
├── deploy/
│   ├── compose.yaml       # Local dev stack
│   ├── compose.prod.yaml  # Production (alternative)
│   ├── k8s/               # Kubernetes manifests
│   └── .env.example       # Environment variables template
├── docs/                  # Architecture & planning docs
├── e2e/                   # End-to-end tests (Python + pytest)
├── Makefile               # Common tasks
└── CLAUDE.md              # Project conventions (local only)
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Agent | Go 1.22+, gRPC, gopsutil, Pion (WebRTC) |
| Backend | Java 21, Spring Boot 3.3+, Maven, Flyway |
| Webapp | React 18+, Vite, Redux Toolkit, RTK Query |
| Database | PostgreSQL 16 + TimescaleDB extension |
| Cache | Redis 7 |
| Message Broker | RabbitMQ 3 |
| Object Storage | MinIO (S3-compatible) |
| Relay | coturn (STUN/TURN) |
| Observability | Prometheus + Grafana + Loki |

