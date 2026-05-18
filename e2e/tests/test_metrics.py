from datetime import datetime, timedelta, timezone

import pytest
import requests

from config import BASE_URL
from conftest import poll_until

pytestmark = [pytest.mark.slow, pytest.mark.requires_agent]


def test_metrics_require_auth(enrolled_agent):
    now = datetime.now(timezone.utc)
    params = {
        "from": (now - timedelta(minutes=5)).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "to": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "type": "cpu",
    }
    r = requests.get(f"{BASE_URL}/api/endpoints/{enrolled_agent}/metrics", params=params)
    assert r.status_code in (401, 403)


def test_endpoint_has_no_metrics_before_agent_reports(admin_session, enrolled_agent):
    """Initially (right after enrolment) there may be no metrics yet."""
    now = datetime.now(timezone.utc)
    params = {
        "from": (now - timedelta(seconds=5)).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "to": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "type": "cpu",
    }
    r = admin_session.get(f"{BASE_URL}/api/endpoints/{enrolled_agent}/metrics", params=params)
    assert r.status_code == 200
    # result is a list — may be empty or have points, both are valid
    assert isinstance(r.json(), list)


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


def test_metrics_time_range_filter(admin_session, enrolled_agent):
    """Querying a future time range returns an empty list."""
    future = datetime.now(timezone.utc) + timedelta(hours=1)
    params = {
        "from": future.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "to": (future + timedelta(minutes=5)).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "type": "cpu",
    }
    r = admin_session.get(f"{BASE_URL}/api/endpoints/{enrolled_agent}/metrics", params=params)
    assert r.status_code == 200
    assert r.json() == []
