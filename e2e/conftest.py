import os
import subprocess
import tempfile
import time

import pytest
import requests

from config import BASE_URL, ADMIN_USERNAME, ADMIN_PASSWORD, AGENT_IMAGE, E2E_NETWORK

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _wait_for_gateway(timeout=90):
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


def _wait_for_enrolment(container_id, timeout=20):
    deadline = time.time() + timeout
    log_content = ""
    while time.time() < deadline:
        result = subprocess.run(
            ["podman", "exec", container_id, "cat", "/var/lib/pulse-agent/logs/agent.log"],
            capture_output=True, text=True,
        )
        log_content = result.stdout
        for line in log_content.splitlines():
            if line.startswith("Enrolled: ") or line.startswith("Already enrolled: "):
                return line.split(": ", 1)[1].strip()
        time.sleep(1)
    container_logs = subprocess.run(["podman", "logs", container_id], capture_output=True, text=True)
    raise RuntimeError(
        f"Agent did not enrol within {timeout}s.\n"
        f"agent.log:\n{log_content}\n"
        f"container stderr:\n{container_logs.stderr}"
    )


def poll_until(fn, timeout=10, interval=0.5):
    """Retry fn until it returns truthy or timeout is reached."""
    deadline = time.time() + timeout
    last_error = None
    while time.time() < deadline:
        try:
            result = fn()
            if result:
                return result
        except Exception as e:
            last_error = e
        time.sleep(interval)
    raise RuntimeError(f"poll_until timeout after {timeout}s. Last error: {last_error}")


def pytest_addoption(parser):
    """Add custom command-line options."""
    parser.addoption(
        "--logs",
        action="store_true",
        help="Show container logs on test failure"
    )
    parser.addoption(
        "--follow-logs",
        action="store_true",
        help="Stream container logs during test execution"
    )


def _get_container_logs(service_name):
    """Fetch logs from a container."""
    try:
        result = subprocess.run(
            ["podman", "compose", "-f", "deploy/compose.yaml", "-f", "deploy/compose.e2e.yaml",
             "--project-name", "pulse-e2e", "logs", service_name],
            capture_output=True, text=True, timeout=5, cwd=REPO_ROOT,
        )
        return result.stdout
    except Exception as e:
        return f"Failed to fetch logs: {e}"


@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item, call):
    """Capture logs on test failure."""
    outcome = yield
    rep = outcome.get_result()

    if rep.failed and item.config.getoption("--logs"):
        print("\n" + "="*80)
        print(f"LOGS FOR FAILED TEST: {item.nodeid}")
        print("="*80)

        services = ["api-gateway", "identity-service", "enrolment-service",
                   "metric-service", "script-service"]
        for service in services:
            logs = _get_container_logs(service)
            if logs:
                print(f"\n--- {service} ---")
                lines = logs.split('\n')
                print('\n'.join(lines[-30:]))  # Last 30 lines


@pytest.fixture(scope="session")
def registered_user():
    """Register a new admin user; assumes clean database."""
    print(f"\n[setup] waiting for gateway at {BASE_URL}...")
    _wait_for_gateway()
    print(f"[setup] registering user '{ADMIN_USERNAME}'...")
    r = requests.post(
        f"{BASE_URL}/api/auth/register",
        json={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD},
    )
    if r.status_code == 409:
        raise RuntimeError(
            "Registration returned 409 — database not clean.\n"
            "Run: make e2e-down"
        )
    assert r.status_code == 201, f"Registration failed: {r.status_code} {r.text}"
    user_id = r.json()["id"]
    print(f"[setup] registered: {user_id}")
    return {"id": user_id, "username": ADMIN_USERNAME}


@pytest.fixture(scope="session")
def admin_session(registered_user):
    """Log in and return authenticated session."""
    print(f"[setup] logging in as '{ADMIN_USERNAME}'...")
    session = requests.Session()
    r = session.post(
        f"{BASE_URL}/api/auth/login",
        json={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD},
    )
    assert r.status_code == 200, r.text
    token = r.json()["accessToken"]
    session.headers.update({"Authorization": f"Bearer {token}"})
    session.token = token
    print(f"[setup] logged in, token: {token[:20]}...")
    return session


@pytest.fixture(scope="session")
def enrolled_agent(admin_session):
    """Create a group, enrolment token, start agent container, and enroll it."""
    group_r = admin_session.post(
        f"{BASE_URL}/api/groups",
        json={"name": "E2eAgentGroup", "parentId": None},
    )
    assert group_r.status_code == 201, group_r.text
    group_id = group_r.json()["id"]
    print(f"[setup] created group: {group_id}")

    token_r = admin_session.post(
        f"{BASE_URL}/api/enrolment/tokens",
        json={"groupId": group_id, "ttlHours": 1},
    )
    assert token_r.status_code == 201, token_r.text
    enrolment_token = token_r.json()["id"]
    print(f"[setup] created enrolment token: {enrolment_token}")

    api_url = os.environ.get("PULSE_API_URL", "http://localhost:8081")
    grpc_addr = os.environ.get("PULSE_GRPC_ADDR", "127.0.0.1:9091")
    tls_enabled = os.environ.get("PULSE_MTLS_ENABLED", "false").lower() == "true"
    cfg_content = (
        f"api_url: {api_url}\n"
        f"grpc_addr: {grpc_addr}\n"
        f"enrolment_token: {enrolment_token}\n"
        f"data_dir: /var/lib/pulse-agent\n"
        f"tls_enabled: {str(tls_enabled).lower()}\n"
    )
    cfg_file = tempfile.NamedTemporaryFile(mode="w", suffix=".yaml", delete=False)
    cfg_file.write(cfg_content)
    cfg_file.close()
    os.chmod(cfg_file.name, 0o644)

    print(f"[setup] starting agent container ({AGENT_IMAGE})...")
    result = subprocess.run(
        [
            "podman", "run", "-d",
            "--log-driver=k8s-file",
            "--network=host",
            "-v", f"{cfg_file.name}:/etc/pulse-agent/config.yaml:ro,z",
            AGENT_IMAGE,
        ],
        capture_output=True, text=True, check=True,
    )
    container_id = result.stdout.strip()
    print(f"[setup] container started: {container_id[:12]}")

    try:
        endpoint_id = _wait_for_enrolment(container_id)
        print(f"[setup] agent enrolled as endpoint: {endpoint_id}")
        yield endpoint_id
    finally:
        print(f"\n[teardown] stopping agent container {container_id[:12]}...")
        subprocess.run(["podman", "stop", "-t", "1", container_id], capture_output=True)
        subprocess.run(["podman", "rm", "-f", container_id], capture_output=True)

@pytest.fixture(params=[
    ("POST", "/api/scripts"),
    ("GET", "/api/scripts/fake-id"),
    ("POST", "/api/scripts/fake-id/approve"),
    ("POST", "/api/scripts/fake-id/run"),
    ("GET", "/api/scripts/runs/fake-id/results"),
    ("POST", "/api/sessions"),
    ("GET", "/api/sessions/fake-id"),
    ("DELETE", "/api/sessions/fake-id"),
    ("GET", "/api/identity/rbac/permissions"),
    ("GET", "/api/identity/rbac/roles"),
    ("POST", "/api/identity/rbac/roles"),
    ("GET", "/api/endpoints"),
    ("POST", "/api/enrolment/tokens"),
    ("GET", "/api/groups"),
    ("POST", "/api/groups"),
    ("POST", "/api/shell/sessions"),
    ("POST", "/api/tags"),
])
def protected_endpoint(request):
    """Parameterized fixture for all protected endpoints."""
    method, path = request.param
    return method, path


pytestmark_auth = pytest.mark.parametrize("protected_endpoint", [
    ("POST", "/api/scripts"),
    ("GET", "/api/scripts/fake-id"),
    ("POST", "/api/scripts/fake-id/approve"),
    ("POST", "/api/scripts/fake-id/run"),
    ("GET", "/api/scripts/runs/fake-id/results"),
    ("POST", "/api/sessions"),
    ("GET", "/api/sessions/fake-id"),
    ("DELETE", "/api/sessions/fake-id"),
    ("GET", "/api/identity/rbac/permissions"),
    ("GET", "/api/identity/rbac/roles"),
    ("POST", "/api/identity/rbac/roles"),
    ("GET", "/api/endpoints"),
    ("POST", "/api/enrolment/tokens"),
    ("GET", "/api/groups"),
    ("POST", "/api/groups"),
    ("POST", "/api/shell/sessions"),
    ("POST", "/api/tags"),
])


def auth_test_endpoints():
    """
    List of (method, path) tuples for protected endpoints.
    Used in test_auth.py as a parametrized test.
    """
    return [
        ("POST", "/api/scripts"),
        ("GET", "/api/scripts/fake-id"),
        ("POST", "/api/scripts/fake-id/approve"),
        ("POST", "/api/scripts/fake-id/run"),
        ("GET", "/api/scripts/runs/fake-id/results"),
        ("POST", "/api/sessions"),
        ("GET", "/api/sessions/fake-id"),
        ("DELETE", "/api/sessions/fake-id"),
        ("GET", "/api/identity/rbac/permissions"),
        ("GET", "/api/identity/rbac/roles"),
        ("POST", "/api/identity/rbac/roles"),
        ("GET", "/api/endpoints"),
        ("POST", "/api/enrolment/tokens"),
        ("GET", "/api/groups"),
        ("POST", "/api/groups"),
        ("PUT", "/api/endpoints/fake-id/tags"),
    ]
