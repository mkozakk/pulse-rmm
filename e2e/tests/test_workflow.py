import os
import time
import pytest
import requests
import websocket

BASE_URL = os.getenv("PULSE_BASE_URL", "http://localhost:8080")
ADMIN_USERNAME = "e2e_admin"
ADMIN_PASSWORD = "e2eadminpassword1"
AGENT_IMAGE = os.getenv("PULSE_AGENT_IMAGE", "pulse-rmm-agent-e2e")
E2E_NETWORK = os.getenv("PULSE_E2E_NETWORK", "pulse-e2e_default")

pytestmark = [pytest.mark.slow, pytest.mark.requires_agent]


# --- helpers ------------------------------------------------------------------

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


# --- fixtures -----------------------------------------------------------------

@pytest.fixture(scope="module")
def registered_user():
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
    print(f"[setup] registered: {r.json()['id']}")


@pytest.fixture(scope="module")
def logged_in_session(registered_user):
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


@pytest.fixture(scope="module")
def enrolled_agent(logged_in_session):
    group_r = logged_in_session.post(
        f"{BASE_URL}/api/groups",
        json={"name": "E2eAgentGroup", "parentId": None},
    )
    assert group_r.status_code == 201, group_r.text
    print(f"[setup] created group: {group_r.json()['id']}")

    token_r = logged_in_session.post(
        f"{BASE_URL}/api/enrolment/tokens",
        json={"groupId": group_r.json()["id"], "ttlHours": 1},
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


# --- tests --------------------------------------------------------------------

def test_endpoint_enrolment(logged_in_session, enrolled_agent):
    print(f"\n[test] checking endpoint {enrolled_agent} appears in list...")
    r = logged_in_session.get(f"{BASE_URL}/api/endpoints")
    assert r.status_code == 200, r.text

    ids = [e["id"] for e in r.json()]
    assert enrolled_agent in ids, f"endpoint {enrolled_agent} not found in {ids}"

    endpoint = next(e for e in r.json() if e["id"] == enrolled_agent)
    print(f"[test] endpoint status: {endpoint['status']}")
    assert endpoint["status"] == "online", f"endpoint: {endpoint}"


def test_remote_shell(logged_in_session, enrolled_agent):
    url = f"ws://localhost:8080/ws/shell/{enrolled_agent}?token={logged_in_session.token[:20]}..."
    print(f"\n[test] opening shell at {url}")

    collected = b""
    deadline = time.time() + 20
    while time.time() < deadline:
        ws = websocket.WebSocket()
        ws.settimeout(5)
        try:
            ws.connect(f"ws://localhost:8080/ws/shell/{enrolled_agent}?token={logged_in_session.token}")
            print("[test] connected, sending: echo e2ehello")
            ws.send_binary(b"\x01" + b"echo e2ehello\n")
            inner = time.time() + 8
            while time.time() < inner:
                try:
                    frame = ws.recv()
                    if isinstance(frame, bytes) and len(frame) > 1 and frame[0] == 0x01:
                        collected += frame[1:]
                    if b"e2ehello" in collected:
                        print(f"[test] received output: {collected!r}")
                        return
                except websocket.WebSocketTimeoutException:
                    break
        except Exception as e:
            print(f"[test] attempt failed: {e}")
        finally:
            try:
                ws.close()
            except Exception:
                pass
        time.sleep(1)

    pytest.fail(f"'e2ehello' not in shell output after 20s. Collected: {collected!r}")


def test_script_execution(logged_in_session, enrolled_agent):
    print(f"\n[test] script execution on {enrolled_agent}")

    print("[test] creating script...")
    r = logged_in_session.post(
        f"{BASE_URL}/api/scripts",
        json={"name": "e2e-test-script", "body": 'echo "e2e-script-ok"'},
    )
    assert r.status_code == 201, r.text
    script_id = r.json()["id"]
    print(f"[test] created script: {script_id}")

    print(f"[test] running script on endpoint {enrolled_agent}...")
    r = logged_in_session.post(
        f"{BASE_URL}/api/scripts/{script_id}/run",
        json={"endpointIds": [enrolled_agent]},
    )
    assert r.status_code == 202, r.text
    run_id = r.json()["runId"]
    print(f"[test] script run initiated: {run_id}")

    print("[test] polling results (30s timeout)...")
    deadline = time.time() + 30
    while time.time() < deadline:
        r = logged_in_session.get(f"{BASE_URL}/api/scripts/runs/{run_id}/results")
        assert r.status_code == 200, r.text
        data = r.json()
        print(f"[test] pending: {data['pending']}, total: {data['total']}")
        if data["pending"] == 0:
            break
        time.sleep(1)
    else:
        pytest.fail("script execution not acked within 30s")

    results = data["results"]
    assert len(results) == 1, f"expected 1 result, got {len(results)}"
    assert results[0]["exitCode"] == 0, f"expected exit code 0, got {results[0]['exitCode']}"
    assert "e2e-script-ok" in results[0]["output"], f"expected 'e2e-script-ok' in output, got: {results[0]['output']}"
    print(f"[test] script executed successfully: {results[0]['output'][:50]}")


def test_approved_library_script_run(logged_in_session, enrolled_agent):
    print(f"\n[test] approved library script on {enrolled_agent}")

    print("[test] creating script...")
    r = logged_in_session.post(
        f"{BASE_URL}/api/scripts",
        json={"name": "e2e-approved-script", "body": "echo approved"},
    )
    assert r.status_code == 201, r.text
    script_id = r.json()["id"]
    print(f"[test] created script: {script_id}")

    print("[test] approving script...")
    r = logged_in_session.post(f"{BASE_URL}/api/scripts/{script_id}/approve")
    assert r.status_code == 200, r.text
    print(f"[test] script approved at: {r.json()['approvedAt']}")

    print(f"[test] running approved script on {enrolled_agent}...")
    r = logged_in_session.post(
        f"{BASE_URL}/api/scripts/{script_id}/run",
        json={"endpointIds": [enrolled_agent]},
    )
    assert r.status_code == 202, r.text
    run_id = r.json()["runId"]
    print(f"[test] script run initiated: {run_id}")

    print("[test] polling results (30s timeout)...")
    deadline = time.time() + 30
    while time.time() < deadline:
        r = logged_in_session.get(f"{BASE_URL}/api/scripts/runs/{run_id}/results")
        assert r.status_code == 200, r.text
        data = r.json()
        if data["pending"] == 0:
            break
        time.sleep(1)
    else:
        pytest.fail("approved script execution not acked within 30s")

    results = data["results"]
    assert len(results) == 1
    assert results[0]["exitCode"] == 0
    assert "approved" in results[0]["output"]
    print(f"[test] approved script executed successfully")
