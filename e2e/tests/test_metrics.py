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


def test_system_info_is_reported(admin_session, enrolled_agent):
    """Agent reports SystemInfo on start; the GET endpoint returns it."""

    def _query():
        r = admin_session.get(f"{BASE_URL}/api/endpoints/{enrolled_agent}/system-info")
        if r.status_code == 404:
            return None
        assert r.status_code == 200, r.text
        return r.json()

    info = poll_until(_query, timeout=40, interval=2)
    assert info["cpuLogical"] is not None and info["cpuLogical"] > 0
    assert info["ramTotal"] is not None and info["ramTotal"] > 0
    # the agent runs in a scratch container with overlay-fs only, which gopsutil filters
    # out of disk.Partitions(all=false); we only assert the wire shape, not host-specific data
    assert isinstance(info["disks"], list)
    assert isinstance(info["nics"], list) and len(info["nics"]) > 0  # loopback always present


def test_system_info_requires_auth(enrolled_agent):
    r = requests.get(f"{BASE_URL}/api/endpoints/{enrolled_agent}/system-info")
    assert r.status_code in (401, 403)


def test_per_core_cpu_samples_carry_labels(admin_session, enrolled_agent):
    """cpu.core samples expose per-core values via the 'core' label."""

    def _query():
        now = datetime.now(timezone.utc)
        params = {
            "from": (now - timedelta(minutes=10)).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "to": (now + timedelta(minutes=1)).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "type": "cpu.core",
        }
        r = admin_session.get(f"{BASE_URL}/api/endpoints/{enrolled_agent}/metrics", params=params)
        assert r.status_code == 200, r.text
        points = r.json()
        return points if points else None

    points = poll_until(_query, timeout=60, interval=2)
    assert any(p.get("labels", {}).get("core") is not None for p in points)


def test_metrics_label_filter_narrows_results(admin_session, enrolled_agent):
    """Filtering cpu.core by label.core=0 only returns core-0 samples."""

    def _query():
        now = datetime.now(timezone.utc)
        params = {
            "from": (now - timedelta(minutes=10)).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "to": (now + timedelta(minutes=1)).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "type": "cpu.core",
            "label.core": "0",
        }
        r = admin_session.get(f"{BASE_URL}/api/endpoints/{enrolled_agent}/metrics", params=params)
        assert r.status_code == 200, r.text
        points = r.json()
        return points if points else None

    points = poll_until(_query, timeout=60, interval=2)
    cores_seen = {p.get("labels", {}).get("core") for p in points}
    assert cores_seen == {"0"}
