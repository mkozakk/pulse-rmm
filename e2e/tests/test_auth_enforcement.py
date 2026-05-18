"""
Consolidated auth enforcement tests for all protected endpoints.
Replaces scattered "requires_auth" tests across test files.
"""
import pytest
import requests

from config import BASE_URL
from conftest import auth_test_endpoints

pytestmark = pytest.mark.fast


@pytest.mark.parametrize("method,path", auth_test_endpoints())
def test_protected_endpoints_require_auth(method, path):
    """All protected endpoints require authentication (401 or 403)."""
    if method == "POST":
        r = requests.post(f"{BASE_URL}{path}", json={})
    elif method == "GET":
        r = requests.get(f"{BASE_URL}{path}")
    elif method == "PUT":
        r = requests.put(f"{BASE_URL}{path}", json={})
    elif method == "DELETE":
        r = requests.delete(f"{BASE_URL}{path}")
    else:
        pytest.fail(f"Unsupported method: {method}")

    assert r.status_code in (401, 403), \
        f"{method} {path} returned {r.status_code}, expected 401/403"
