import pytest
import requests

from config import BASE_URL

# Auth enforcement tests consolidated in test_auth_enforcement.py


def test_create_token_with_valid_jwt(admin_session):
    """Creating an enrolment token with valid JWT returns 201."""
    group_r = admin_session.post(
        f"{BASE_URL}/api/groups",
        json={"name": "TestGroup", "parentId": None},
    )
    assert group_r.status_code == 201
    group_id = group_r.json()["id"]

    r = admin_session.post(
        f"{BASE_URL}/api/enrolment/tokens",
        json={"groupId": group_id, "ttlHours": 24},
    )
    assert r.status_code == 201
    assert "id" in r.json()
    assert "expiresAt" in r.json()


def test_list_endpoints_empty(admin_session):
    """List endpoints returns 200 with list (may have enrolled agents)."""
    r = admin_session.get(f"{BASE_URL}/api/endpoints")
    assert r.status_code == 200
    assert isinstance(r.json(), list)


@pytest.mark.slow
@pytest.mark.requires_agent
def test_endpoint_enrolled_and_online(admin_session, enrolled_agent):
    """Real agent enrolls and appears in endpoint list with online status."""
    r = admin_session.get(f"{BASE_URL}/api/endpoints")
    assert r.status_code == 200
    ids = [e["id"] for e in r.json()]
    assert enrolled_agent in ids

    endpoint = next(e for e in r.json() if e["id"] == enrolled_agent)
    assert endpoint["status"] == "online", f"expected online, got: {endpoint}"
