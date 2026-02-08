import os
import subprocess
import time

import pytest
import requests

from config import BASE_URL, ADMIN_USERNAME, ADMIN_PASSWORD, AGENT_IMAGE, E2E_NETWORK


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
    logs = ""
    while time.time() < deadline:
        result = subprocess.run(["podman", "logs", container_id], capture_output=True, text=True)
        logs = result.stdout
        for line in logs.splitlines():
            if line.startswith("Enrolled: ") or line.startswith("Already enrolled: "):
                return line.split(": ", 1)[1].strip()
        time.sleep(1)
    raise RuntimeError(f"Agent did not enrol within {timeout}s.\ncontainer stdout:\n{logs}")


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
            capture_output=True, text=True, timeout=5
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

    print(f"[setup] starting agent container ({AGENT_IMAGE})...")
    result = subprocess.run(
        [
            "podman", "run", "-d", "--rm",
            "--network", E2E_NETWORK,
            "-e", f"PULSE_TOKEN={enrolment_token}",
            "-e", "PULSE_SERVER=enrolment-service:9091",
            "-e", "PULSE_METRIC_SERVER=metric-service:9092",
            "-e", "PULSE_GATEWAY=api-gateway:9090",
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
        subprocess.run(["podman", "stop", container_id], capture_output=True)
