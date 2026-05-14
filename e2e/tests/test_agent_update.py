import io
import pytest
import requests

from config import BASE_URL

pytestmark = pytest.mark.fast

DUMMY_BINARY = b"ELF fake agent binary content for testing"


def _publish(session, version="1.0.0", os_name="linux", arch="amd64"):
    files = {"file": ("pulse-agent", io.BytesIO(DUMMY_BINARY), "application/octet-stream")}
    data = {"version": version, "os": os_name, "arch": arch}
    r = session.post(f"{BASE_URL}/api/agent-versions", files=files, data=data)
    return r


def test_publish_version(admin_session):
    r = _publish(admin_session, version="2.0.0")
    assert r.status_code == 201
    body = r.json()
    assert body["version"] == "2.0.0"
    assert body["os"] == "linux"
    assert body["arch"] == "amd64"
    assert len(body["sha256"]) == 64
    assert body["sizeBytes"] > 0
    assert body["current"] is False
    assert "id" in body


def test_publish_duplicate_returns_conflict(admin_session):
    _publish(admin_session, version="2.1.0")
    r = _publish(admin_session, version="2.1.0")
    assert r.status_code == 409


def test_list_versions(admin_session):
    _publish(admin_session, version="2.2.0")
    r = admin_session.get(f"{BASE_URL}/api/agent-versions")
    assert r.status_code == 200
    versions = r.json()
    assert any(v["version"] == "2.2.0" for v in versions)


def test_set_current_and_update_check(admin_session):
    # publish two versions
    r1 = _publish(admin_session, version="3.0.0")
    assert r1.status_code == 201
    v_id = r1.json()["id"]

    # set as current
    r = admin_session.put(f"{BASE_URL}/api/agent-versions/{v_id}/current")
    assert r.status_code == 200
    assert r.json()["current"] is True

    # agent on older version should get an update
    r = admin_session.get(f"{BASE_URL}/api/updates/check",
                          params={"os": "linux", "arch": "amd64", "version": "2.9.9"})
    assert r.status_code == 200
    body = r.json()
    assert body["upToDate"] is False
    assert body["version"] == "3.0.0"
    assert body["downloadUrl"] is not None
    assert len(body["sha256"]) == 64

    # agent already on current version
    r = admin_session.get(f"{BASE_URL}/api/updates/check",
                          params={"os": "linux", "arch": "amd64", "version": "3.0.0"})
    assert r.status_code == 200
    assert r.json()["upToDate"] is True


def test_only_one_current_per_platform(admin_session):
    r1 = _publish(admin_session, version="4.0.0")
    r2 = _publish(admin_session, version="4.1.0")
    assert r1.status_code == 201
    assert r2.status_code == 201

    id1, id2 = r1.json()["id"], r2.json()["id"]
    admin_session.put(f"{BASE_URL}/api/agent-versions/{id1}/current")
    admin_session.put(f"{BASE_URL}/api/agent-versions/{id2}/current")

    r = admin_session.get(f"{BASE_URL}/api/agent-versions")
    current_versions = [v for v in r.json()
                        if v["os"] == "linux" and v["arch"] == "amd64" and v["current"]]
    assert len(current_versions) == 1
    assert current_versions[0]["version"] == "4.1.0"


def test_delete_non_current_version(admin_session):
    r = _publish(admin_session, version="5.0.0")
    assert r.status_code == 201
    v_id = r.json()["id"]

    r = admin_session.delete(f"{BASE_URL}/api/agent-versions/{v_id}")
    assert r.status_code == 204


def test_delete_current_version_blocked(admin_session):
    r = _publish(admin_session, version="6.0.0")
    assert r.status_code == 201
    v_id = r.json()["id"]
    admin_session.put(f"{BASE_URL}/api/agent-versions/{v_id}/current")

    r = admin_session.delete(f"{BASE_URL}/api/agent-versions/{v_id}")
    assert r.status_code == 409


def test_unauthenticated_returns_401(admin_session):
    r = requests.get(f"{BASE_URL}/api/agent-versions")
    assert r.status_code == 401

    # /api/updates/check is public — agents call it without a user JWT
    r = requests.get(f"{BASE_URL}/api/updates/check",
                     params={"os": "linux", "arch": "amd64", "version": "1.0.0"})
    assert r.status_code == 200
