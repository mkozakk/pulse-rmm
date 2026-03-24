import os
import subprocess
import tempfile
import time

import pytest
import requests

from config import BASE_URL, AGENT_IMAGE
from conftest import _wait_for_enrolment, poll_until


pytestmark = [pytest.mark.slow, pytest.mark.requires_agent]


def _start_agent(token):
    api_url = os.environ.get("PULSE_API_URL", "http://localhost:8081")
    grpc_addr = os.environ.get("PULSE_GRPC_ADDR", "127.0.0.1:9091")
    cfg = (
        f"api_url: {api_url}\n"
        f"grpc_addr: {grpc_addr}\n"
        f"enrolment_token: {token}\n"
        f"data_dir: /var/lib/pulse-agent\n"
    )
    f = tempfile.NamedTemporaryFile(mode="w", suffix=".yaml", delete=False)
    f.write(cfg)
    f.close()
    os.chmod(f.name, 0o644)
    r = subprocess.run(
        ["podman", "run", "-d", "--log-driver=k8s-file", "--network=host",
         "-v", f"{f.name}:/etc/pulse-agent/config.yaml:ro,z", AGENT_IMAGE],
        capture_output=True, text=True, check=True,
    )
    return r.stdout.strip()


def _agent_log(container_id):
    r = subprocess.run(
        ["podman", "exec", container_id, "cat", "/var/lib/pulse-agent/logs/agent.log"],
        capture_output=True, text=True,
    )
    return r.stdout


def test_revoked_endpoint_is_locked_out(admin_session):
    """After POST /api/endpoints/{id}/revoke, the agent's mTLS RPCs are denied
    by the gateway (UNAUTHENTICATED)."""
    group_r = admin_session.post(
        f"{BASE_URL}/api/groups",
        json={"name": "RevokeGroup", "parentId": None},
    )
    assert group_r.status_code == 201, group_r.text
    group_id = group_r.json()["id"]

    token_r = admin_session.post(
        f"{BASE_URL}/api/enrolment/tokens",
        json={"groupId": group_id, "ttlHours": 1},
    )
    assert token_r.status_code == 201, token_r.text
    token = token_r.json()["id"]

    container = _start_agent(token)
    try:
        endpoint_id = _wait_for_enrolment(container)

        # Wait until at least one heartbeat has succeeded so we know the
        # post-revoke failure isn't masking an unrelated startup error.
        poll_until(lambda: "heartbeat error" not in _agent_log(container)
                          and "Enrolled:" in _agent_log(container),
                   timeout=30)

        revoke = admin_session.post(
            f"{BASE_URL}/api/endpoints/{endpoint_id}/revoke",
            json={"reason": "e2e test"},
        )
        assert revoke.status_code == 204, revoke.text

        # Cache TTL on the gateway is 5s; agent heartbeats every 30s. Give it
        # enough wall time for the next tick to land after the cache invalidates.
        def saw_revocation_rejection():
            log = _agent_log(container)
            return ("UNAUTHENTICATED" in log and "endpoint revoked" in log) \
                   or "endpoint revoked" in log

        poll_until(saw_revocation_rejection, timeout=60, interval=2)
    finally:
        subprocess.run(["podman", "stop", "-t", "1", container], capture_output=True)
        subprocess.run(["podman", "rm", "-f", container], capture_output=True)
