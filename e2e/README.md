# E2E Tests

Pure API e2e tests for Pulse RMM. Tests run against a fully containerised stack, including a real agent container, and cover the core user workflow through the API gateway.

## Prerequisites

- Podman + podman-compose
- Python 3.10+

## Setup

```bash
cp deploy/.env.e2e.example deploy/.env.e2e
pip install -r e2e/requirements.txt
```

The defaults in `.env.e2e.example` work for local development.

## Running

```bash
# Full clean run (recommended — destroys e2e volumes first, then runs tests)
make e2e

# Start the e2e stack without running tests
make e2e-up

# Tear down e2e stack and destroy e2e volumes (dev volumes are untouched)
make e2e-down

# Run all tests against an already-running e2e stack
cd e2e && python -m pytest tests/ -v

# Run a single test file
cd e2e && python -m pytest tests/test_workflow.py -v

# Run a single test by name
cd e2e && python -m pytest tests/test_workflow.py::test_remote_shell -v
```

The stack must already be running (`make e2e-up`) when running pytest directly. The `bootstrap_admin` fixture registers the admin user on first run — if you re-run without `make e2e-down` first, it will fail with a 409 error (see Idempotency below).

## Test coverage

### `test_workflow.py`

One test file covering the full core workflow end-to-end.

**Setup (handled by fixtures):**
1. `bootstrap_admin` — registers the first user (becomes admin)
2. `admin_session` — logs in and holds an authenticated HTTP session
3. `enrolled_agent` — creates a group + enrolment token, starts a real agent container, waits for it to enrol

**Tests:**

| Test | What it validates |
|------|-------------------|
| `test_endpoint_enrolment` | Enrolled endpoint appears in `GET /api/endpoints` with `status: online` |
| `test_remote_shell` | WebSocket shell opens to the enrolled endpoint, `echo e2ehello` returns output containing `e2ehello` |

**`test_remote_shell` detail:** connects to `ws://localhost:8080/ws/shell/{id}?token={jwt}`, sends a binary frame (`0x01` + stdin bytes), collects output frames (`0x01` + stdout bytes). Retries for up to 20s to allow the agent's control stream to connect after enrolment.

## Idempotency

Tests require a clean database. `make e2e` always runs `make e2e-down` first to destroy the e2e volumes. The e2e stack uses separate named volumes (`postgres_e2e_data` etc.) so dev data is never affected.

If you run `pytest` directly against a database that already has a user, `conftest.py` will fail with:

```
RuntimeError: Registration returned 409 — database not clean.
Run: make e2e-down
```
