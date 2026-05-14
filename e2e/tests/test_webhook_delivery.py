import hashlib
import hmac
import http.server
import json
import socket
import socketserver
import threading
import time

import pytest
import requests

from config import BASE_URL

pytestmark = pytest.mark.slow

HOST_FROM_CONTAINER = "host.containers.internal"

_ALERT_RULE = {
    "name": "webhook delivery test rule",
    "metricType": "cpu",
    "operator": ">",
    "threshold": 90.0,
    "durationSecs": 300,
    "target": {"type": "tag", "value": "env=prod"},
}

_SECRET = "webhook_e2e_test_secret_16chars!"


def _free_port():
    with socket.socket() as s:
        s.bind(("", 0))
        return s.getsockname()[1]


class MockWebhookServer:
    def __init__(self, port, responses=None, delay=0):
        self.received = []
        self._responses = list(responses or [200])
        self._delay = delay
        outer = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self, fmt, *args):
                pass

            def do_POST(self):
                n = int(self.headers.get("Content-Length", 0))
                body = self.rfile.read(n) if n else b""
                idx = len(outer.received)
                code = outer._responses[min(idx, len(outer._responses) - 1)]
                outer.received.append({"headers": dict(self.headers), "body": body})
                if outer._delay:
                    time.sleep(outer._delay)
                self.send_response(code)
                self.end_headers()

        class TCPServer(socketserver.ThreadingTCPServer):
            allow_reuse_address = True

        self._server = TCPServer(("0.0.0.0", port), Handler)
        self._thread = threading.Thread(target=self._server.serve_forever, daemon=True)

    def start(self):
        self._thread.start()

    def stop(self):
        self._server.shutdown()


def _wait_for(condition, timeout=30, interval=1):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if condition():
            return True
        time.sleep(interval)
    return False


def _create_webhook(session, url, event_types):
    r = session.post(f"{BASE_URL}/api/webhooks", json={
        "url": url,
        "eventTypes": event_types,
        "secret": _SECRET,
    })
    assert r.status_code == 201, r.text
    return r.json()["id"]


def _trigger_audit_event(session):
    r = session.post(f"{BASE_URL}/api/alert-rules", json=_ALERT_RULE)
    assert r.status_code == 201, r.text
    return r.json()["id"]


def _verify_signature(body: bytes, secret: str, header: str) -> bool:
    expected = "sha256=" + hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, header)


def test_successful_delivery(admin_session):
    port = _free_port()
    srv = MockWebhookServer(port)
    srv.start()
    webhook_id = None
    rule_id = None
    try:
        webhook_url = f"http://{HOST_FROM_CONTAINER}:{port}/webhook"
        webhook_id = _create_webhook(admin_session, webhook_url, ["audit.*"])

        rule_id = _trigger_audit_event(admin_session)

        assert _wait_for(lambda: len(srv.received) >= 1, timeout=15), \
            "mock server never received a webhook POST"

        req = srv.received[0]
        body = req["body"]
        headers = req["headers"]

        assert "X-Pulse-Signature" in headers
        assert _verify_signature(body, _SECRET, headers["X-Pulse-Signature"]), \
            f"signature mismatch: {headers['X-Pulse-Signature']}"
        assert headers.get("X-Pulse-Event", "").startswith("audit.")
        assert headers.get("User-Agent") == "PulseRMM-Webhook/1.0"

        payload = json.loads(body)
        assert "id" in payload
        assert "type" in payload
        assert payload["type"].startswith("audit.")
        assert "data" in payload
    finally:
        srv.stop()
        if webhook_id:
            admin_session.delete(f"{BASE_URL}/api/webhooks/{webhook_id}")
        if rule_id:
            admin_session.delete(f"{BASE_URL}/api/alert-rules/{rule_id}")


def test_retry_on_500(admin_session):
    port = _free_port()
    # returns 500 twice, then 200
    srv = MockWebhookServer(port, responses=[500, 500, 200])
    srv.start()
    webhook_id = None
    rule_id = None
    try:
        webhook_url = f"http://{HOST_FROM_CONTAINER}:{port}/webhook"
        webhook_id = _create_webhook(admin_session, webhook_url, ["audit.*"])

        rule_id = _trigger_audit_event(admin_session)

        # 3 attempts: immediate + retry after ~1s + retry after ~4s + scheduler delays (5s each)
        assert _wait_for(lambda: len(srv.received) >= 3, timeout=40), \
            f"expected 3 requests, got {len(srv.received)}"

        assert len(srv.received) == 3, f"expected exactly 3 attempts, got {len(srv.received)}"
    finally:
        srv.stop()
        if webhook_id:
            admin_session.delete(f"{BASE_URL}/api/webhooks/{webhook_id}")
        if rule_id:
            admin_session.delete(f"{BASE_URL}/api/alert-rules/{rule_id}")


def test_dead_letter_after_3_failures(admin_session):
    port = _free_port()
    srv = MockWebhookServer(port, responses=[500, 500, 500])
    srv.start()
    webhook_id = None
    rule_id = None
    try:
        webhook_url = f"http://{HOST_FROM_CONTAINER}:{port}/webhook"
        webhook_id = _create_webhook(admin_session, webhook_url, ["audit.*"])

        rule_id = _trigger_audit_event(admin_session)

        assert _wait_for(lambda: len(srv.received) >= 3, timeout=40), \
            f"expected 3 requests, got {len(srv.received)}"

        # wait extra time to confirm no 4th attempt
        time.sleep(10)
        assert len(srv.received) == 3, f"expected dead_letter after 3 attempts, got {len(srv.received)}"
    finally:
        srv.stop()
        if webhook_id:
            admin_session.delete(f"{BASE_URL}/api/webhooks/{webhook_id}")
        if rule_id:
            admin_session.delete(f"{BASE_URL}/api/alert-rules/{rule_id}")


def test_no_retry_on_400(admin_session):
    port = _free_port()
    srv = MockWebhookServer(port, responses=[400])
    srv.start()
    webhook_id = None
    rule_id = None
    try:
        webhook_url = f"http://{HOST_FROM_CONTAINER}:{port}/webhook"
        webhook_id = _create_webhook(admin_session, webhook_url, ["audit.*"])

        rule_id = _trigger_audit_event(admin_session)

        assert _wait_for(lambda: len(srv.received) >= 1, timeout=15), \
            "mock server never received initial POST"

        # 400 is non-retryable — wait and confirm no retry
        time.sleep(8)
        assert len(srv.received) == 1, f"expected 1 attempt for 400, got {len(srv.received)}"
    finally:
        srv.stop()
        if webhook_id:
            admin_session.delete(f"{BASE_URL}/api/webhooks/{webhook_id}")
        if rule_id:
            admin_session.delete(f"{BASE_URL}/api/alert-rules/{rule_id}")
