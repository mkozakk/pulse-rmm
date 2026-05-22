import pytest
import requests

from config import BASE_URL

pytestmark = pytest.mark.fast

_VALID_RULE = {
    "name": "high cpu",
    "metricType": "cpu",
    "operator": ">",
    "threshold": 90.0,
    "durationSecs": 300,
    "target": {"type": "tag", "value": "env=prod"},
}


def test_alert_rules_require_auth():
    r = requests.post(f"{BASE_URL}/api/alert-rules", json=_VALID_RULE)
    assert r.status_code in (401, 403)

    r = requests.get(f"{BASE_URL}/api/alert-rules")
    assert r.status_code in (401, 403)


def test_alert_rule_crud(admin_session):
    # create
    r = admin_session.post(f"{BASE_URL}/api/alert-rules", json=_VALID_RULE)
    assert r.status_code == 201
    rule = r.json()
    rule_id = rule["id"]
    assert rule["metricType"] == "cpu"
    assert rule["operator"] == ">"
    assert rule["enabled"] is True

    # list
    r = admin_session.get(f"{BASE_URL}/api/alert-rules")
    assert r.status_code == 200
    assert any(x["id"] == rule_id for x in r.json())

    # validation — bad metric type
    r = admin_session.post(f"{BASE_URL}/api/alert-rules", json={**_VALID_RULE, "metricType": "temperature"})
    assert r.status_code == 400

    # delete
    r = admin_session.delete(f"{BASE_URL}/api/alert-rules/{rule_id}")
    assert r.status_code == 204

    r = admin_session.get(f"{BASE_URL}/api/alert-rules")
    assert not any(x["id"] == rule_id for x in r.json())
