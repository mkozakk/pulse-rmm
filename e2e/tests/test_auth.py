import pytest
import requests

from config import (
    BASE_URL, KEYCLOAK_URL, KEYCLOAK_REALM, KEYCLOAK_E2E_CLIENT,
    ADMIN_USERNAME, ADMIN_PASSWORD,
)

pytestmark = pytest.mark.fast

TOKEN_URL = f"{KEYCLOAK_URL}/realms/{KEYCLOAK_REALM}/protocol/openid-connect/token"


def _token(username, password):
    return requests.post(TOKEN_URL, data={
        "grant_type": "password",
        "client_id": KEYCLOAK_E2E_CLIENT,
        "username": username,
        "password": password,
    })


def test_keycloak_issues_token_for_admin(registered_user):
    r = _token(ADMIN_USERNAME, ADMIN_PASSWORD)
    assert r.status_code == 200, r.text
    assert "access_token" in r.json()


def test_wrong_password_rejected(registered_user):
    r = _token(ADMIN_USERNAME, "wrongpass123")
    assert r.status_code == 401


def test_keycloak_token_grants_api_access(admin_session):
    """A Keycloak-issued token is accepted by the gateway (validated via JWKS)."""
    r = admin_session.get(f"{BASE_URL}/api/groups")
    assert r.status_code == 200


def test_seeded_admin_has_rbac_permissions(admin_session):
    """The realm-imported admin's sub is seeded the Admin role in rbac-service."""
    r = admin_session.get(f"{BASE_URL}/api/identity/rbac/permissions")
    assert r.status_code == 200
