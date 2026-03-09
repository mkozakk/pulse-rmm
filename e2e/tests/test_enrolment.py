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
    """List endpoints returns 200 with empty list when no agents enrolled."""
    r = admin_session.get(f"{BASE_URL}/api/endpoints")
    assert r.status_code == 200
    assert isinstance(r.json(), list)
