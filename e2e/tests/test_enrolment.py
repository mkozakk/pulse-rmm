import requests

from config import BASE_URL


def test_create_token_requires_auth():
    """Creating an enrolment token without auth returns 403."""
    group_id = "00000000-0000-0000-0000-000000000000"
    payload = {"groupId": group_id, "ttlHours": 24}
    r = requests.post(f"{BASE_URL}/api/enrolment/tokens", json=payload)
    assert r.status_code == 403


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


def test_list_endpoints_requires_auth():
    """List endpoints without auth returns 403."""
    r = requests.get(f"{BASE_URL}/api/endpoints")
    assert r.status_code == 403
