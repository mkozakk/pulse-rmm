from datetime import datetime, timedelta, timezone

import requests

from config import BASE_URL
from conftest import poll_until


def test_metrics_require_auth(enrolled_agent):
    now = datetime.now(timezone.utc)
    params = {
        "from": (now - timedelta(minutes=5)).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "to": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "type": "cpu",
    }
    r = requests.get(f"{BASE_URL}/api/endpoints/{enrolled_agent}/metrics", params=params)
    assert r.status_code in (401, 403)


def test_enrolled_agent_metrics_appear_in_api(admin_session, enrolled_agent):
    def _query():
        now = datetime.now(timezone.utc)
        params = {
            "from": (now - timedelta(minutes=10)).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "to": (now + timedelta(minutes=1)).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "type": "cpu",
        }
        r = admin_session.get(f"{BASE_URL}/api/endpoints/{enrolled_agent}/metrics", params=params)
        assert r.status_code == 200, r.text
        points = r.json()
        if points:
            return points
        return None

    points = poll_until(_query, timeout=40, interval=2)
    assert len(points) > 0
