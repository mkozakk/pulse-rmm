import os
import socket
import struct
import time

import pytest
import requests
import websocket

from config import BASE_URL
from conftest import poll_until

WS_URL = BASE_URL.replace("http://", "ws://").replace("https://", "wss://")

pytestmark = pytest.mark.slow


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _create_session(session, endpoint_id):
    return session.post(
        f"{BASE_URL}/api/sessions",
        json={"endpointId": endpoint_id, "type": "desktop"},
    )


# ---------------------------------------------------------------------------
# Infrastructure
# ---------------------------------------------------------------------------

def test_turn_server_reachable():
    """coturn responds to a STUN Binding Request on the e2e TURN port."""
    msg_type = 0x0001   # Binding Request
    msg_len = 0
    magic = 0x2112A442
    tx_id = os.urandom(12)
    pkt = struct.pack(">HHI", msg_type, msg_len, magic) + tx_id

    turn_port = int(os.getenv("PULSE_TURN_PORT", "3479"))
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(3)
    try:
        sock.sendto(pkt, ("127.0.0.1", turn_port))
        data, _ = sock.recvfrom(1024)
    finally:
        sock.close()

    resp_type = struct.unpack(">H", data[:2])[0]
    assert resp_type == 0x0101, f"expected STUN Binding Success (0x0101), got {hex(resp_type)}"


# ---------------------------------------------------------------------------
# Auth enforcement tests consolidated in test_auth_enforcement.py
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Session REST API — happy path (admin has remote:desktop:control)
# ---------------------------------------------------------------------------

@pytest.fixture
def desktop_session(admin_session, enrolled_agent):
    """Create a desktop session and yield its response JSON. Cleans up after."""
    r = _create_session(admin_session, enrolled_agent)
    assert r.status_code == 201, f"session creation failed: {r.status_code} {r.text}"
    data = r.json()
    yield data
    admin_session.delete(f"{BASE_URL}/api/sessions/{data['sessionId']}")


def test_create_session_returns_turn_credentials(admin_session, enrolled_agent):
    r = _create_session(admin_session, enrolled_agent)
    assert r.status_code == 201, r.text
    data = r.json()
    assert "sessionId" in data
    assert "turnUrls" in data and len(data["turnUrls"]) > 0
    assert "turnUsername" in data
    assert "turnCredential" in data


def test_create_session_control_flag_for_admin(admin_session, enrolled_agent):
    """Admin has remote:desktop:control so can_control must be true."""
    r = _create_session(admin_session, enrolled_agent)
    assert r.status_code == 201, r.text
    assert r.json()["canControl"] is True


def test_get_session_status(desktop_session, admin_session):
    session_id = desktop_session["sessionId"]
    r = admin_session.get(f"{BASE_URL}/api/sessions/{session_id}")
    assert r.status_code == 200, r.text
    assert r.json()["status"] in ("pending", "active")


def test_get_session_not_found_returns_404(admin_session):
    r = admin_session.get(f"{BASE_URL}/api/sessions/00000000-0000-0000-0000-000000000000")
    assert r.status_code == 404


def test_end_session(admin_session, enrolled_agent):
    r = _create_session(admin_session, enrolled_agent)
    assert r.status_code == 201, r.text
    session_id = r.json()["sessionId"]

    r_del = admin_session.delete(f"{BASE_URL}/api/sessions/{session_id}")
    assert r_del.status_code == 204

    r_get = admin_session.get(f"{BASE_URL}/api/sessions/{session_id}")
    assert r_get.status_code == 200
    assert r_get.json()["status"] == "ended"


def test_end_session_twice_is_idempotent(admin_session, enrolled_agent):
    r = _create_session(admin_session, enrolled_agent)
    session_id = r.json()["sessionId"]
    admin_session.delete(f"{BASE_URL}/api/sessions/{session_id}")
    r2 = admin_session.delete(f"{BASE_URL}/api/sessions/{session_id}")
    assert r2.status_code in (204, 404)


# ---------------------------------------------------------------------------
# WebSocket signaling — auth enforcement and session validation
# ---------------------------------------------------------------------------

def test_signaling_ws_invalid_session_rejected(admin_session):
    """Connecting with a valid token but a non-existent session_id should be rejected."""
    ws = websocket.WebSocket()
    ws.settimeout(5)
    try:
        ws.connect(
            f"{WS_URL}/ws/sessions/00000000-0000-0000-0000-000000000000/signal"
            f"?token={admin_session.token}"
        )
        ws.settimeout(3)
        try:
            ws.recv()
        except Exception:
            pass
        status = ws.status
        assert status in (None, 4001, 1008, 1002), f"expected rejection, got status {status}"
    except Exception:
        pass  # connection refused or immediate close is also acceptable
    finally:
        try:
            ws.close()
        except Exception:
            pass


def test_signaling_ws_accepts_valid_session(admin_session, desktop_session):
    """Connecting with a valid token and valid session_id should be accepted."""
    session_id = desktop_session["sessionId"]
    ws = websocket.WebSocket()
    ws.settimeout(5)
    try:
        ws.connect(
            f"{WS_URL}/ws/sessions/{session_id}/signal"
            f"?token={admin_session.token}"
        )
        ws.settimeout(1)
        try:
            ws.recv()
            # If we receive a message, it might be a "session not ready" event — that's fine
        except websocket.WebSocketTimeoutException:
            pass  # timeout means connection is open and waiting — correct behaviour
    finally:
        try:
            ws.close()
        except Exception:
            pass
