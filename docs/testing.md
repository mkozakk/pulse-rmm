# Testing Guide

This guide covers unit tests, integration tests, and end-to-end tests in Pulse RMM.

## Prerequisites

### Podman Socket Setup

Integration tests use Testcontainers which requires a podman socket. Set this up once:

```bash
# Enable the podman socket for your user
systemctl --user enable --now podman.socket

# Verify it's running
systemctl --user status podman.socket
```

### Environment Variables

Add these to your shell profile (`.zshrc`, `.bashrc`, or `.profile`) to persist them:

```bash
export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
export TESTCONTAINERS_CHECKS_DISABLE=true
```

Or run them in your terminal before running tests. These tell Testcontainers to use podman instead of Docker and disable problematic cleanup features.

## Backend (Java)

Always run the backend tests through the Makefile. It sets `JAVA_HOME`, points Testcontainers at the rootless podman socket (`DOCKER_HOST`), and configures surefire/failsafe so that integration tests actually execute — a plain `mvn` invocation gets none of that right.

### Unit Tests

Unit tests are fast, in-memory tests that verify business logic without external dependencies.

```bash
make tests-unit
```

Runs only the tests that don't require containers (it skips the integration tests).

### Integration Tests

Integration tests start a real PostgreSQL database in a container and exercise the full request path.

```bash
make tests-it
```

Runs the integration tests (named `*IT.java`) against a real database. Use `make tests` to run unit and integration tests together.

**What happens:**
1. Testcontainers starts a PostgreSQL 16 container
2. Flyway migrations run automatically
3. Tests execute with real database I/O
4. Container is cleaned up after tests finish

**First run:** Takes ~30–60s (container startup). Subsequent runs are faster.

### Running Tests for a Specific Service

Use the `service=` parameter to scope a run to one module:

```bash
make tests service=endpoint-service
make tests-it service=metric-service
```

### Code Coverage Reports (JaCoCo)

Backend tests generate JaCoCo code coverage reports automatically when the Makefile runs them.

**View coverage after running tests:**

```bash
make tests

# Unit test coverage
open backend/api-gateway/target/site/jacoco/index.html

# Integration test coverage
open backend/api-gateway/target/site/jacoco-it/index.html
```

Replace `api-gateway` with the service name (e.g., `rbac-service`, `endpoint-service`).

**What's measured:**
- Unit tests: coverage from `*Test.java` files
- Integration tests: coverage from `*IT.java` files with real database calls

**How to use reports:**
- Green lines: code executed by tests
- Red lines: code not executed (uncovered)
- Click classes/methods to drill down

**Example:**
```bash
make tests service=api-gateway
# Open the HTML report in your browser
firefox backend/api-gateway/target/site/jacoco/index.html
```

## Agent (Go)

### Unit and Integration Tests

Go tests are run with the standard `go test` command. The agent has no external dependencies in tests.

```bash
cd agent
go test ./...
```

Runs all tests in the agent module.

**With verbose output:**
```bash
cd agent
go test -v ./...
```

**Run tests in a specific package:**
```bash
cd agent/cmd
go test -v
```

## Webapp (React)

### Unit and Integration Tests

The webapp uses Vitest for fast unit testing.

```bash
cd webapp
npm run test
```

Runs all tests matching `*.test.js` or `*.spec.js` in watch mode.

**Single run (CI mode):**
```bash
npm run test -- --run
```

**With coverage:**
```bash
npm run test -- --coverage
```

## End-to-End Tests (E2E)

E2E tests exercise complete user workflows: authentication, RBAC, enrolment, endpoints, tags. They start the full containerized stack (api-gateway, identity-service, enrolment-service, postgres, redis, rabbitmq, minio) and make real HTTP requests.

### Prerequisites

E2E tests require:
- Python 3.8+
- `make` command
- podman (same setup as backend tests above)
- `.env.e2e` file in `deploy/` directory

### Setup

Copy the E2E environment template:

```bash
cp deploy/.env.e2e.example deploy/.env.e2e
```

The `.env.e2e` file is gitignored. Review it and adjust if needed (defaults work for local dev).

### Running E2E Tests

```bash
make e2e
```

This:
1. Stops any previous e2e stack (`make e2e-down`)
2. Starts a fresh stack with clean volumes (`make e2e-up`)
3. Runs all Python tests in `e2e/tests/`
4. Tears down the stack

**First run:** Takes 60–90s (container startup + bootstrap). Subsequent runs are faster.

### E2E Stack Commands

**Start the stack (keep running):**
```bash
make e2e-up
```

**View logs:**
```bash
make e2e-logs
```

**Stop the stack:**
```bash
make e2e-down
```

**Run tests against a running stack:**
```bash
cd e2e
python -m pytest tests/ -v
```

**Run a specific test:**
```bash
cd e2e
python -m pytest tests/test_auth.py::test_login_returns_token_and_cookie -v
```

### E2E Test Organization

Tests are in `e2e/tests/`:

| File | Coverage |
|------|----------|
| `test_auth.py` | Login, refresh, logout, credential validation |
| `test_rbac.py` | Permissions catalog, default roles, role CRUD |
| `test_enrolment_tokens.py` | Token creation, auth checks |
| `test_endpoints.py` | Endpoint CRUD, group nesting, depth limits, tag filters |
| `test_tag_rules.py` | Tag rule CRUD, rule evaluation, auth checks |

### E2E Troubleshooting

**"Database not clean" error (409 on register):**
```bash
make e2e-down
```

**Stack won't start:**
```bash
make e2e-logs
# Review error messages, then:
make e2e-down
make e2e-up
```

**Tests timeout waiting for services:**
- Check `make e2e-logs` - look for service startup errors
- Ensure podman is running: `podman ps`

## Running All Tests

### Everything (unit + integration + e2e)

```bash
# Backend unit + integration
make tests

# Agent tests
cd agent && go test ./...

# Webapp tests
cd webapp && npm run test -- --run

# E2E tests
make e2e
```

### Before Committing

Run this checklist:

```bash
# 1. Backend tests must pass
make tests

# 2. Agent tests must pass
cd agent && go test ./...

# 3. (Optional) Webapp tests
cd webapp && npm run test -- --run

# 4. (Optional) E2E tests - these take longer
make e2e
```

## Test Naming Conventions

**Backend (Java):**
- Unit tests: `*Test.java` (e.g., `JwtServiceTest.java`)
- Integration tests: `*IT.java` (e.g., `LoginIT.java`)
- Located alongside source code in `src/test/java`

**Agent (Go):**
- Test files: `*_test.go` (e.g., `metrics_test.go`)
- Located in the same package as source code

**Webapp (React):**
- Test files: `*.test.js` or `*.spec.js`
- Located alongside or in a `__tests__` directory

**E2E (Python):**
- Test files in `e2e/tests/test_*.py`
- Organized by feature/flow (auth, rbac, enrolment, etc.)

## Performance Tips

1. **First-run overhead:** Testcontainers and podman startups take 30–90s. Subsequent runs use cached containers.

2. **Run only what you changed:**
   - Editing a service? `make tests service=<name>` runs just that module instead of the whole backend.
   - Editing the agent? `go test ./...` in the agent directory.
   - Editing a component in the webapp? Run its test file only.

3. **Skip E2E during development:** E2E tests are comprehensive but slow. Run unit + integration tests while developing, then run E2E before opening a PR.

4. **Check code coverage:** JaCoCo reports are generated automatically when the Makefile runs the tests. Open `backend/<service>/target/site/jacoco/index.html` to see which code is tested. Red lines = untested code that may need test coverage.

## Troubleshooting

### Testcontainers Errors

**"Could not find a valid Docker environment"**
- Ensure podman socket is enabled: `systemctl --user status podman.socket`
- Check `DOCKER_HOST` is set: `echo $DOCKER_HOST`
- Restart podman: `systemctl --user restart podman.socket`

**"Permission denied" when starting containers**
- Add your user to the `podman` group:
  ```bash
  sudo usermod -aG podman $USER
  newgrp podman
  ```

**Tests pass locally but fail in CI**
- CI may use Docker instead of podman. Check `.github/workflows/` for test job configuration.
- Ensure all environment variables are set in CI context.

### Build Issues

**"BUILD FAILURE"**
```bash
cd backend && mvn clean    # wipe stale target/ dirs, then re-run
make tests
```

**OutOfMemoryError during tests**
```bash
export MAVEN_OPTS="-Xmx2g"
make tests
```

### Vitest Issues

**"Module not found"**
```bash
cd webapp
npm install
npm run test -- --run
```

**Port already in use**
- Vitest uses a random port by default. If you get a port conflict, stop any other `npm run dev` processes.

### E2E Issues

**"Gateway not healthy after 90s"**
- Check `make e2e-logs` for service errors
- Ensure docker/podman is available: `podman ps`
- Try again: `make e2e-down && make e2e`

**Flaky tests (pass sometimes, fail other times)**
- E2E tests wait for services to be healthy but startup times vary. If flakiness persists, increase timeouts in `e2e/conftest.py`.
- Check logs: `make e2e-logs` for slow services or connection errors.

## Further Reading

- **Backend architecture:** See `docs/architecture.md`
- **Dev plan & acceptance criteria:** See `docs/dev-plan.md` for sprint-specific test requirements
- **E2E testing design:** See `docs/e2e-testing.md` for the full E2E test strategy and scenarios
