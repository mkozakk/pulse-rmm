"""
E2E tests for user management (POST/GET/PUT/DELETE /api/identity/users).
"""
import pytest
import requests

from config import BASE_URL, KEYCLOAK_URL, KEYCLOAK_REALM, KEYCLOAK_E2E_CLIENT, ADMIN_USERNAME, ADMIN_PASSWORD

pytestmark = pytest.mark.fast


def test_list_users_requires_auth():
    r = requests.get(f"{BASE_URL}/api/identity/users")
    assert r.status_code in (401, 403)


def test_list_users_returns_admin(admin_session):
    r = admin_session.get(f"{BASE_URL}/api/identity/users")
    assert r.status_code == 200
    users = r.json()
    usernames = [u["username"] for u in users]
    assert ADMIN_USERNAME in usernames


def test_create_and_delete_user(admin_session):
    r = admin_session.post(
        f"{BASE_URL}/api/identity/users",
        json={
            "username": "e2e_temp_user",
            "email": "e2e_temp@test.local",
            "firstName": "E2E",
            "lastName": "Temp",
            "password": "temppass123",
            "roleName": "Auditor",
        },
    )
    assert r.status_code == 201, r.text
    user_id = r.json()["id"]
    assert r.json()["username"] == "e2e_temp_user"

    # Can log in with the new user
    token_r = requests.post(
        f"{KEYCLOAK_URL}/realms/{KEYCLOAK_REALM}/protocol/openid-connect/token",
        data={
            "grant_type": "password",
            "client_id": KEYCLOAK_E2E_CLIENT,
            "username": "e2e_temp_user",
            "password": "temppass123",
        },
    )
    assert token_r.status_code == 200, f"New user can't login: {token_r.text}"

    # Delete
    del_r = admin_session.delete(f"{BASE_URL}/api/identity/users/{user_id}")
    assert del_r.status_code == 204

    # Gone from list
    list_r = admin_session.get(f"{BASE_URL}/api/identity/users")
    ids = [u["id"] for u in list_r.json()]
    assert user_id not in ids


def test_update_user_roles(admin_session):
    # Create user
    r = admin_session.post(
        f"{BASE_URL}/api/identity/users",
        json={
            "username": "e2e_roles_user",
            "email": "e2e_roles@test.local",
            "firstName": "Roles",
            "lastName": "Test",
            "password": "rolespass123",
        },
    )
    assert r.status_code == 201, r.text
    user_id = r.json()["id"]

    try:
        # Get Auditor role id
        roles_r = admin_session.get(f"{BASE_URL}/api/identity/rbac/roles")
        roles = roles_r.json()
        auditor = next(ro for ro in roles if ro["name"] == "Auditor")

        # Assign Auditor role
        put_r = admin_session.put(
            f"{BASE_URL}/api/identity/users/{user_id}/roles",
            json={"roleIds": [auditor["id"]]},
        )
        assert put_r.status_code == 200

        # Verify role appears in user response
        get_r = admin_session.get(f"{BASE_URL}/api/identity/users/{user_id}")
        assert get_r.status_code == 200
        assert "Auditor" in get_r.json()["roles"]
    finally:
        admin_session.delete(f"{BASE_URL}/api/identity/users/{user_id}")
