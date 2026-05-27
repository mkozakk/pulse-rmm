import subprocess

import pytest
import requests

from config import BASE_URL, AGENT_IMAGE
from conftest import poll_until

pytestmark = pytest.mark.fast

FAKE_ENDPOINT_ID = "00000000-0000-0000-0000-000000000777"


def test_refresh_without_auth_is_unauthorized():
    r = requests.post(f"{BASE_URL}/api/endpoints/{FAKE_ENDPOINT_ID}/processes/refresh")
    assert r.status_code == 401, r.text


def test_refresh_returns_201_with_command_id(admin_session):
    r = admin_session.post(f"{BASE_URL}/api/endpoints/{FAKE_ENDPOINT_ID}/processes/refresh")
    assert r.status_code == 201, r.text
    assert r.json().get("commandId")


def test_latest_returns_404_when_no_snapshot(admin_session):
    r = admin_session.get(f"{BASE_URL}/api/endpoints/{FAKE_ENDPOINT_ID}/processes/latest")
    assert r.status_code == 404, r.text


def test_kill_returns_202(admin_session):
    r = admin_session.post(f"{BASE_URL}/api/endpoints/{FAKE_ENDPOINT_ID}/processes/1234/kill")
    assert r.status_code == 202, r.text
    assert r.json().get("commandId")


@pytest.mark.slow
@pytest.mark.requires_agent
def test_list_processes_includes_agent_process(admin_session, enrolled_agent):
    """Refresh, wait for ack, latest snapshot should include the agent's own process."""
    refresh = admin_session.post(
        f"{BASE_URL}/api/endpoints/{enrolled_agent}/processes/refresh"
    )
    assert refresh.status_code == 201, refresh.text

    def latest_completed():
        r = admin_session.get(f"{BASE_URL}/api/endpoints/{enrolled_agent}/processes/latest")
        if r.status_code != 200:
            return None
        data = r.json()
        return data if data.get("status") == "COMPLETED" else None

    data = poll_until(latest_completed, timeout=15)
    names = [p["name"] for p in data["processes"]]
    assert any("pulse-agent" in n or "agent" in n for n in names), names


@pytest.mark.slow
@pytest.mark.requires_agent
def test_kill_process_removes_it_from_next_snapshot(admin_session, enrolled_agent):
    """Spawn a sleep on the agent host, kill via API, confirm it's gone."""
    # Find the agent container so we can spawn a process inside it
    container = subprocess.run(
        ["podman", "ps", "--filter", f"ancestor={AGENT_IMAGE}", "--format", "{{.ID}}"],
        capture_output=True, text=True,
    ).stdout.strip().splitlines()
    assert container, f"no agent container running ({AGENT_IMAGE})"
    cid = container[0]

    # Spawn a long-running sleep inside the agent container
    subprocess.run(["podman", "exec", "-d", cid, "sleep", "9999"], check=True)

    def snapshot_with_sleep():
        admin_session.post(f"{BASE_URL}/api/endpoints/{enrolled_agent}/processes/refresh")
        import time
        time.sleep(1.5)
        r = admin_session.get(f"{BASE_URL}/api/endpoints/{enrolled_agent}/processes/latest")
        if r.status_code != 200:
            return None
        data = r.json()
        if data.get("status") != "COMPLETED":
            return None
        sleeps = [p for p in data["processes"] if p["name"] == "sleep"]
        return sleeps[0] if sleeps else None

    sleep_proc = poll_until(snapshot_with_sleep, timeout=30, interval=1)
    pid = sleep_proc["pid"]

    # Kill it
    kill_r = admin_session.post(
        f"{BASE_URL}/api/endpoints/{enrolled_agent}/processes/{pid}/kill"
    )
    assert kill_r.status_code == 202

    def snapshot_without_pid():
        admin_session.post(f"{BASE_URL}/api/endpoints/{enrolled_agent}/processes/refresh")
        import time
        time.sleep(1.5)
        r = admin_session.get(f"{BASE_URL}/api/endpoints/{enrolled_agent}/processes/latest")
        data = r.json()
        if data.get("status") != "COMPLETED":
            return None
        pids = [p["pid"] for p in data["processes"]]
        return pid not in pids

    assert poll_until(snapshot_without_pid, timeout=30, interval=1)
