import pytest
import requests

from config import BASE_URL

pytestmark = pytest.mark.fast


def test_list_permissions_returns_all_permissions(admin_session):
    """GET /api/identity/rbac/permissions returns catalog of all permissions."""
    r = admin_session.get(f"{BASE_URL}/api/identity/rbac/permissions")
    assert r.status_code == 200, r.text
    permissions = r.json()
    assert isinstance(permissions, list)
    assert len(permissions) > 0

    # Check key permissions from catalog exist
    permission_names = [p["name"] for p in permissions]
    assert "endpoint:view" in permission_names
    assert "endpoint:act" in permission_names
    assert "remote:shell" in permission_names
    assert "remote:desktop:control" in permission_names
    assert "script:run" in permission_names
    assert "identity:rbac:manage" in permission_names


def test_list_permissions_requires_auth():
    """GET /api/identity/rbac/permissions without auth returns 403."""
    r = requests.get(f"{BASE_URL}/api/identity/rbac/permissions")
    assert r.status_code in (401, 403)


def test_list_roles_returns_default_roles(admin_session):
    """GET /api/identity/rbac/roles returns default roles."""
    r = admin_session.get(f"{BASE_URL}/api/identity/rbac/roles")
    assert r.status_code == 200, r.text
    roles = r.json()
    assert isinstance(roles, list)
    assert len(roles) > 0

    # Check default roles exist
    role_names = [role["name"] for role in roles]
    assert "Admin" in role_names
    assert "Senior Technician" in role_names
    assert "Junior Technician" in role_names
    assert "Auditor" in role_names


def test_list_roles_requires_auth():
    """GET /api/identity/rbac/roles without auth returns 403."""
    r = requests.get(f"{BASE_URL}/api/identity/rbac/roles")
    assert r.status_code in (401, 403)


def test_create_custom_role(admin_session):
    """POST /api/identity/rbac/roles creates a custom role."""
    role_name = "Custom Inspector"
    r = admin_session.post(
        f"{BASE_URL}/api/identity/rbac/roles",
        json={"name": role_name},
    )
    assert r.status_code == 201, r.text
    created = r.json()
    assert created["id"]
    assert created["name"] == role_name

    # Verify role appears in list
    list_r = admin_session.get(f"{BASE_URL}/api/identity/rbac/roles")
    assert list_r.status_code == 200
    ids = [role["id"] for role in list_r.json()]
    assert created["id"] in ids


def test_create_role_requires_auth():
    """POST /api/identity/rbac/roles without auth returns 403."""
    r = requests.post(
        f"{BASE_URL}/api/identity/rbac/roles",
        json={"name": "Unauthorized Role"},
    )
    assert r.status_code in (401, 403)


def test_admin_user_has_all_permissions(admin_session):
    """Admin user (default) can access protected endpoints."""
    # Admin should be able to list endpoints (requires endpoint:view)
    r = admin_session.get(f"{BASE_URL}/api/endpoints")
    assert r.status_code == 200

    # Admin should be able to create enrolment tokens (requires enrolment:manage)
    group_r = admin_session.post(
        f"{BASE_URL}/api/groups",
        json={"name": "AdminTestGroup", "parentId": None},
    )
    assert group_r.status_code == 201
    group_id = group_r.json()["id"]

    token_r = admin_session.post(
        f"{BASE_URL}/api/enrolment/tokens",
        json={"groupId": group_id, "ttlHours": 1},
    )
    assert token_r.status_code == 201


def test_list_endpoints_requires_auth():
    """GET /api/endpoints without auth returns 403."""
    r = requests.get(f"{BASE_URL}/api/endpoints")
    assert r.status_code in (401, 403)


def test_create_enrolment_token_requires_auth():
    """POST /api/enrolment/tokens without auth returns 403."""
    group_id = "00000000-0000-0000-0000-000000000000"
    r = requests.post(
        f"{BASE_URL}/api/enrolment/tokens",
        json={"groupId": group_id, "ttlHours": 24},
    )
    assert r.status_code in (401, 403)


def test_list_groups_requires_auth():
    """GET /api/groups without auth returns 403."""
    r = requests.get(f"{BASE_URL}/api/groups")
    assert r.status_code in (401, 403)


def test_create_group_requires_auth():
    """POST /api/groups without auth returns 403."""
    r = requests.post(
        f"{BASE_URL}/api/groups",
        json={"name": "Unauthorized", "parentId": None},
    )
    assert r.status_code in (401, 403)
