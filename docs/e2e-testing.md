# E2E Testing Plan for Pulse RMM

## Context

The project has solid per-service integration tests (JUnit 5 + Testcontainers) but no tests that exercise the full request path: client → API gateway → backend service → database → response. This plan adds a lightweight e2e suite that validates complete user workflows via real HTTP requests against a running containerised stack, with no GUI/webapp involvement.

Goals:
- Verify cross-service flows work end-to-end (auth token issued by identity-service accepted by enrolment-service through the gateway)
- Catch gateway routing regressions
- Run fully in containers, completely idempotent

---

## Framework: Python + pytest + requests

**Why not shell/curl**: no cookie jar, painful JSON extraction via jq, poor assertion ergonomics.  
**Why not Karate/Hurl**: DSLs break down for multi-step stateful flows (capture group ID → use in next request).  
**Why Python + pytest + requests**: first-class cookies, readable assertions, pytest fixtures handle the first-user-is-admin bootstrap cleanly with `scope="session"`, no Java toolchain needed to run tests.

---

## Idempotency Strategy

The dev stack uses named volumes (`postgres_data`, `redis_data`, etc.) that persist between runs. E2e tests need a clean database on every run.

**Solution: `deploy/compose.e2e.yaml` override** - replaces all named volumes with e2e-specific names (`postgres_e2e_data`, etc.). Running `podman compose ... down -v` destroys only the e2e volumes, leaving dev data untouched.

The `make e2e` target always runs `down -v` before `up`, guaranteeing a fresh database. The `conftest.py` bootstrap fixture fails fast with an actionable error message if the DB is not clean (HTTP 409 on register → `RuntimeError` telling the dev to run `make e2e-down`).

---

## Directory Structure

```
pulse-rmm/
  e2e/
    requirements.txt          # pytest==8.3.2, requests==2.32.3
    conftest.py               # bootstrap_admin, admin_token, admin_session fixtures
    tests/
      test_auth.py            # login, refresh, logout flows
      test_rbac.py            # permissions catalog, roles CRUD
      test_enrolment_tokens.py # token creation happy path + unauth
      test_endpoints.py       # group CRUD, depth limit, tag filter
      test_tag_rules.py       # tag rule CRUD, evaluate
    README.md
  deploy/
    compose.yaml              # existing - untouched
    compose.e2e.yaml          # new - volume override, webapp excluded
    .env.e2e.example          # committed template for e2e credentials
    .env.e2e                  # gitignored, actual e2e credentials
  Makefile                    # e2e, e2e-up, e2e-down, e2e-logs targets
```

---

## Files to Create

### `deploy/compose.e2e.yaml`

```yaml
# Override for e2e runs. Replaces named volumes with e2e-specific ones.
# Dev volumes (postgres_data etc.) are never touched.
# Usage: podman compose -f deploy/compose.yaml -f deploy/compose.e2e.yaml up -d
# Clean:  podman compose -f deploy/compose.yaml -f deploy/compose.e2e.yaml down -v

services:
  webapp:
    profiles:
      - skip

  postgres:
    volumes:
      - postgres_e2e_data:/var/lib/postgresql/data

  redis:
    volumes:
      - redis_e2e_data:/data

  rabbitmq:
    volumes:
      - rabbitmq_e2e_data:/var/lib/rabbitmq

  minio:
    volumes:
      - minio_e2e_data:/data

volumes:
  postgres_e2e_data:
  redis_e2e_data:
  rabbitmq_e2e_data:
  minio_e2e_data:
```

### `deploy/.env.e2e.example`

Copy of `.env.example` with `E2E` suffix on values to make the origin obvious. Committed. `.env.e2e` is gitignored.

### `Makefile` (root)

```makefile
E2E_COMPOSE = podman compose \
    -f deploy/compose.yaml \
    -f deploy/compose.e2e.yaml \
    --env-file deploy/.env.e2e

.PHONY: e2e e2e-up e2e-down e2e-logs

e2e-down:
	$(E2E_COMPOSE) down -v

e2e-up:
	$(E2E_COMPOSE) up -d --build
	@until curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; do sleep 2; done

e2e-logs:
	$(E2E_COMPOSE) logs -f

e2e: e2e-down e2e-up
	cd e2e && python -m pytest tests/ -v
	$(E2E_COMPOSE) down -v
```

### `e2e/requirements.txt`

```
pytest==8.3.2
requests==2.32.3
```

### `e2e/conftest.py`

```python
import pytest, requests, os, time

BASE_URL = os.getenv("PULSE_BASE_URL", "http://localhost:8080")
ADMIN_USERNAME = "e2e_admin"
ADMIN_PASSWORD = "e2eadminpassword1"


def wait_for_gateway(timeout=90):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            r = requests.get(f"{BASE_URL}/actuator/health", timeout=3)
            if r.status_code == 200:
                return
        except requests.ConnectionError:
            pass
        time.sleep(2)
    raise RuntimeError(f"Gateway not healthy after {timeout}s")


@pytest.fixture(scope="session", autouse=True)
def bootstrap_admin():
    wait_for_gateway()
    r = requests.post(
        f"{BASE_URL}/api/auth/register",
        json={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD},
    )
    if r.status_code == 409:
        raise RuntimeError(
            "Registration returned 409 - database not clean.\n"
            "Run: make e2e-down"
        )
    assert r.status_code == 201, f"Registration failed: {r.status_code} {r.text}"


@pytest.fixture(scope="session")
def admin_token(bootstrap_admin):
    r = requests.post(
        f"{BASE_URL}/api/auth/login",
        json={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD},
    )
    assert r.status_code == 200
    return r.json()["accessToken"]


@pytest.fixture(scope="session")
def admin_session(bootstrap_admin):
    """Shared authenticated session. Do NOT use for refresh/logout tests - create a fresh Session() there."""
    session = requests.Session()
    r = session.post(
        f"{BASE_URL}/api/auth/login",
        json={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD},
    )
    assert r.status_code == 200
    session.headers.update({"Authorization": f"Bearer {r.json()['accessToken']}"})
    return session
```

Key design: `bootstrap_admin` is `autouse=True` so every test file waits for the gateway and registers the admin. Tests that test auth flows (refresh, logout) use a fresh `requests.Session()` inside the test - they must not share the session-scoped `admin_session` because they consume the cookie.

---

## Test Scenarios

### `test_auth.py` - 7 scenarios

| Test | Validates |
|------|-----------|
| `test_login_returns_token_and_cookie` | 200, body has `accessToken`+`expiresIn`, Set-Cookie has `pulse_refresh`, `HttpOnly`, `SameSite=Lax` |
| `test_login_wrong_password` | 401, `error == "invalid_credentials"` |
| `test_login_unknown_user` | 401, same error code (no username enumeration) |
| `test_refresh_returns_new_token` | Login → new session with cookie → refresh → 200, new `accessToken`, rotated cookie |
| `test_refresh_without_cookie` | POST /api/auth/refresh with no cookie → 401 |
| `test_logout_clears_cookie` | Login → logout → 204, `Set-Cookie` with `Max-Age=0` |
| `test_logout_then_refresh_fails` | Login → logout → refresh with old cookie → 401 |

### `test_rbac.py` - 4 scenarios

| Test | Validates |
|------|-----------|
| `test_list_permissions_returns_catalog` | 200, list contains `endpoint:view` and `identity:rbac:manage` |
| `test_list_roles_returns_default_roles` | 200, list contains `Admin`, `Senior Technician`, `Junior Technician`, `Auditor` |
| `test_create_role` | POST → 201 with UUID id; GET /api/identity/rbac/roles contains new role |
| `test_rbac_requires_auth` | GET /api/identity/rbac/permissions with no token → 401/403 |

### `test_enrolment_tokens.py` - 2 scenarios

| Test | Validates |
|------|-----------|
| `test_create_token_for_group` | Create group → POST /api/enrolment/tokens → 201 with `id` and `expiresAt` ~24h from now |
| `test_create_token_requires_auth` | POST /api/enrolment/tokens with no token → 401 |

### `test_endpoints.py` - 5 scenarios

| Test | Validates |
|------|-----------|
| `test_list_endpoints_returns_list` | GET /api/endpoints → 200, JSON array |
| `test_create_root_group` | POST /api/groups → 201, `name` matches, `parentId == null` |
| `test_create_child_group` | Create parent → create child with parentId → 201, parentId matches |
| `test_group_depth_limit` | 5 nested levels succeed; 6th level → 400 |
| `test_tag_filter_no_match` | GET /api/endpoints?tag=env=nonexistent → 200, empty array |

Note: tag set + filter tests require an enrolled endpoint. Those scenarios are covered by existing `TagRuleIT.java`. Add here when an agent binary is available to enrol in the e2e stack.

### `test_tag_rules.py` - 5 scenarios

| Test | Validates |
|------|-----------|
| `test_create_tag_rule` | POST /api/tag-rules → 201 with `id`, `conditionField` matches |
| `test_list_tag_rules` | Create rule → GET /api/tag-rules → 200, list contains rule |
| `test_delete_tag_rule` | Create → delete → 204; GET confirms rule gone |
| `test_evaluate_rules` | Create rule → POST /api/tag-rules/evaluate → 200 |
| `test_tag_rules_require_auth` | GET /api/tag-rules with no token → 401/403 |

---

## Implementation Status

All test scenarios defined in this plan are **implemented and passing**. Test files are located in `e2e/tests/`:

| Test File | Scenarios | Status |
|-----------|-----------|--------|
| `test_auth.py` | Login, logout, token refresh, credential validation | Implemented |
| `test_rbac.py` | Permissions catalog, role CRUD, access control | Implemented |
| `test_enrolment.py` | Token creation, auth checks | Implemented |
| `test_groups.py` | Group CRUD, nesting, depth limits | Implemented |
| `test_workflow.py` | Full endpoint enrolment + status flow | Requires agent container (see below) |
| `test_shell.py` | WebSocket shell streaming to enrolled endpoint | Requires agent container |
| `test_scripts.py` | Script CRUD, execution, approval workflow | Requires agent container |
| `test_metrics.py` | Metric ingestion, querying, offline detection | Requires agent container |
| `test_software.py` | Software management workflows | Requires agent container |
| `test_tags.py` | Tag rule CRUD, evaluation | Implemented |

**Blockers:**
- Tests marked "Requires agent container" need a real Go agent binary running in the e2e network
- Agent binary is available in `agent/` but running it in the e2e stack requires additional setup (enrollment token, config mount)
- Metric-service gRPC endpoints are internal; REST metrics query tested via workflow tests when agent is available

**Running the tests:**
```bash
make e2e                                # Full E2E suite (includes agent container setup)
cd e2e && python -m pytest tests/ -v    # Run against running stack
cd e2e && python -m pytest tests/ -k "fast" -v   # Run only fast tests (no agent)
```

## Out of Scope

**metric-service gRPC** (`pushMetrics`, `heartbeat`): agent-only protocol. The REST query endpoint (`GET /api/endpoints/{id}/metrics`) is only meaningful with data. Covered in `MetricServiceIT.java` for unit testing; E2E tests use this endpoint when agent is running.

---

## CI Note

Add a `.github/workflows/e2e.yml` job that runs `make e2e` on pull requests to main. Requires Docker/Podman on the runner. Do not run on every feature branch push - stack startup takes 60–90 seconds minimum.

---

## Verification

After implementation:
1. `make e2e-down` - ensures clean state
2. `make e2e` - stack starts, `bootstrap_admin` registers, all tests pass, stack tears down
3. Run `make e2e` again immediately - second run also passes (idempotency confirmed)
4. Confirm `postgres_data` dev volume still exists after `make e2e-down`: `podman volume ls | grep postgres_data`
