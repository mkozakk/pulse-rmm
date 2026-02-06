import pytest
import requests

from config import BASE_URL

pytestmark = [pytest.mark.slow, pytest.mark.requires_agent]


def test_endpoint_enrolment(admin_session, enrolled_agent):
    print(f"\n[test] checking endpoint {enrolled_agent} appears in list...")
    r = admin_session.get(f"{BASE_URL}/api/endpoints")
    assert r.status_code == 200, r.text

    ids = [e["id"] for e in r.json()]
    assert enrolled_agent in ids, f"endpoint {enrolled_agent} not found in {ids}"

    endpoint = next(e for e in r.json() if e["id"] == enrolled_agent)
    print(f"[test] endpoint status: {endpoint['status']}")
    assert endpoint["status"] == "online", f"endpoint: {endpoint}"
