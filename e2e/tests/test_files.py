import io
import os

import pytest

from config import BASE_URL
from conftest import poll_until

pytestmark = pytest.mark.fast


def _list(session, endpoint_id, path=None):
    params = {"path": path} if path else {}
    r = session.get(f"{BASE_URL}/api/files/{endpoint_id}", params=params)
    assert r.status_code == 200, r.text
    return r.json()


def test_list_roots(admin_session, enrolled_agent):
    """No path → agent returns filesystem roots. On Linux agent that's '/'."""
    data = poll_until(lambda: _list(admin_session, enrolled_agent), timeout=20)
    names = [e["name"] for e in data["entries"]]
    assert "/" in names, f"expected '/' in roots, got {names}"


def test_upload_then_list_then_download(admin_session, enrolled_agent):
    """Upload a small payload, see it in the listing, download it back, verify content."""
    payload = os.urandom(128 * 1024)  # 128KB — crosses the 64KB chunk boundary
    dest = "/tmp/pulse-e2e-upload.bin"

    up = admin_session.post(
        f"{BASE_URL}/api/files/{enrolled_agent}/upload",
        params={"path": dest},
        files={"file": ("upload.bin", io.BytesIO(payload), "application/octet-stream")},
    )
    assert up.status_code == 200, up.text
    assert up.json()["bytes"] == len(payload)

    listing = _list(admin_session, enrolled_agent, path="/tmp")
    names = {e["name"]: e for e in listing["entries"]}
    assert "pulse-e2e-upload.bin" in names
    assert names["pulse-e2e-upload.bin"]["size"] == len(payload)

    dl = admin_session.get(
        f"{BASE_URL}/api/files/{enrolled_agent}/download",
        params={"path": dest},
    )
    assert dl.status_code == 200, dl.text
    assert dl.content == payload


def test_list_missing_path(admin_session, enrolled_agent):
    """Non-existent path returns 400 with error detail."""
    r = admin_session.get(
        f"{BASE_URL}/api/files/{enrolled_agent}",
        params={"path": "/no/such/dir/xyz123"},
    )
    assert r.status_code == 400, r.text
    assert "detail" in r.json()
