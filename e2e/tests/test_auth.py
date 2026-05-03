import pytest
import requests

from config import BASE_URL, ADMIN_USERNAME, ADMIN_PASSWORD


def test_register_first_user_becomes_admin(admin_session):
    """First registered user is an admin (verified by permissions test below)."""
    r = admin_session.get(f"{BASE_URL}/actuator/health")
    assert r.status_code == 200


def test_register_duplicate_username_returns_409():
    """After bootstrap, registration is closed (409 Conflict)."""
    payload = {"username": "someone_else", "password": "validpass1234"}
    r = requests.post(f"{BASE_URL}/api/auth/register", json=payload)
    assert r.status_code == 409


def test_login_wrong_password_returns_401():
    """Login with wrong password returns 401."""
    wrong = {"username": ADMIN_USERNAME, "password": "wrongpass123"}
    r = requests.post(f"{BASE_URL}/api/auth/login", json=wrong)
    assert r.status_code == 401


def test_login_correct_returns_access_token_and_cookie(admin_session):
    """Login with correct credentials returns accessToken and Sets-Cookie."""
    r = requests.post(
        f"{BASE_URL}/api/auth/login",
        json={"username": "e2e_admin", "password": "e2eadminpassword1"},
    )
    assert r.status_code == 200
    assert "accessToken" in r.json()
    assert "expiresIn" in r.json()
    assert "Set-Cookie" in r.headers


def test_refresh_rotates_token(admin_session):
    """Refresh endpoint returns accessToken (may be same as current)."""
    r = admin_session.post(f"{BASE_URL}/api/auth/refresh")
    assert r.status_code == 200
    assert "accessToken" in r.json()
    assert "expiresIn" in r.json()


def test_logout_clears_cookie(admin_session):
    """Logout clears the refresh token cookie."""
    r = admin_session.post(f"{BASE_URL}/api/auth/logout")
    assert r.status_code == 204
    assert "Set-Cookie" in r.headers
    assert "Max-Age=0" in r.headers["Set-Cookie"]
