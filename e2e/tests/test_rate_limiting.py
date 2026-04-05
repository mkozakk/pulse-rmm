import random
import requests
import pytest

from config import BASE_URL

pytestmark = pytest.mark.fast

# Must exceed the default 200/min capacity to guarantee a 429
BURST_SIZE = 250


def _unique_ip():
    """Each call returns a fresh fake IP for an isolated rate-limit bucket."""
    return f"10.{random.randint(1, 254)}.{random.randint(1, 254)}.{random.randint(1, 254)}"


def test_rate_limit_triggers_on_burst(registered_user):
    """A burst of unauthenticated requests eventually hits the rate limit."""
    ip = _unique_ip()
    responses = [
        requests.get(f"{BASE_URL}/api/groups", headers={"X-Forwarded-For": ip})
        for _ in range(BURST_SIZE)
    ]
    assert any(r.status_code == 429 for r in responses), \
        f"Expected at least one 429 after {BURST_SIZE} requests, got: {set(r.status_code for r in responses)}"


def test_rate_limit_response_has_retry_after(registered_user):
    """429 response includes a Retry-After header."""
    ip = _unique_ip()
    responses = [
        requests.get(f"{BASE_URL}/api/groups", headers={"X-Forwarded-For": ip})
        for _ in range(BURST_SIZE)
    ]
    limited = next((r for r in responses if r.status_code == 429), None)
    assert limited is not None, f"No 429 response in {BURST_SIZE} requests"
    assert limited.headers.get("Retry-After") is not None, \
        f"429 missing Retry-After. Headers: {dict(limited.headers)}"
