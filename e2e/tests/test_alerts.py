import pytest
import requests

from config import BASE_URL

pytestmark = pytest.mark.fast

FAKE_RULE_ID = "00000000-0000-0000-0000-000000000099"

_VALID_RULE = {
    "name": "high cpu",
    "metricType": "cpu",
    "operator": ">",
    "threshold": 90.0,
    "durationSecs": 300,
    "target": {"type": "tag", "value": "env=prod"},
}


def _create_rule(session, payload=None):
    r = session.post(f"{BASE_URL}/api/alert-rules", json=payload or _VALID_RULE)
    assert r.status_code == 201, r.text
    return r.json()


# --- auth / permission checks -------------------------------------------------

def test_create_rule_requires_auth():
    r = requests.post(f"{BASE_URL}/api/alert-rules", json=_VALID_RULE)
    assert r.status_code in (401, 403)


def test_list_rules_requires_auth():
    r = requests.get(f"{BASE_URL}/api/alert-rules")
    assert r.status_code in (401, 403)


def test_delete_rule_requires_auth():
    r = requests.delete(f"{BASE_URL}/api/alert-rules/{FAKE_RULE_ID}")
    assert r.status_code in (401, 403)


# --- happy path ---------------------------------------------------------------

def test_create_rule_returns_201(admin_session):
    rule = _create_rule(admin_session, {**_VALID_RULE, "name": "create-test"})
    assert rule["id"]
    assert rule["name"] == "create-test"
    assert rule["metricType"] == "cpu"
    assert rule["operator"] == ">"
    assert rule["threshold"] == 90.0
    assert rule["durationSecs"] == 300
    assert rule["targetType"] == "tag"
    assert rule["targetValue"] == "env=prod"
    assert rule["enabled"] is True


def test_create_rule_location_header(admin_session):
    r = admin_session.post(f"{BASE_URL}/api/alert-rules", json={**_VALID_RULE, "name": "location-test"})
    assert r.status_code == 201
    assert "location" in r.headers


def test_list_rules_returns_created_rule(admin_session):
    name = "list-test-rule"
    _create_rule(admin_session, {**_VALID_RULE, "name": name})
    r = admin_session.get(f"{BASE_URL}/api/alert-rules")
    assert r.status_code == 200
    names = [rule["name"] for rule in r.json()]
    assert name in names


def test_delete_rule(admin_session):
    rule = _create_rule(admin_session, {**_VALID_RULE, "name": "to-delete"})
    rule_id = rule["id"]

    r = admin_session.delete(f"{BASE_URL}/api/alert-rules/{rule_id}")
    assert r.status_code == 204

    remaining = admin_session.get(f"{BASE_URL}/api/alert-rules").json()
    assert not any(r["id"] == rule_id for r in remaining)


def test_delete_nonexistent_rule_returns_404(admin_session):
    r = admin_session.delete(f"{BASE_URL}/api/alert-rules/{FAKE_RULE_ID}")
    assert r.status_code == 404


# --- validation ---------------------------------------------------------------

def test_create_rule_missing_name_returns_400(admin_session):
    payload = {**_VALID_RULE}
    del payload["name"]
    r = admin_session.post(f"{BASE_URL}/api/alert-rules", json=payload)
    assert r.status_code == 400


def test_create_rule_invalid_metric_type_returns_400(admin_session):
    r = admin_session.post(f"{BASE_URL}/api/alert-rules", json={**_VALID_RULE, "metricType": "temperature"})
    assert r.status_code == 400


def test_create_rule_invalid_operator_returns_400(admin_session):
    r = admin_session.post(f"{BASE_URL}/api/alert-rules", json={**_VALID_RULE, "operator": "=="})
    assert r.status_code == 400


def test_create_rule_duration_too_short_returns_400(admin_session):
    r = admin_session.post(f"{BASE_URL}/api/alert-rules", json={**_VALID_RULE, "durationSecs": 10})
    assert r.status_code == 400


def test_create_rule_invalid_target_type_returns_400(admin_session):
    r = admin_session.post(f"{BASE_URL}/api/alert-rules", json={
        **_VALID_RULE,
        "target": {"type": "region", "value": "us-east-1"},
    })
    assert r.status_code == 400


def test_create_rule_with_ram_metric(admin_session):
    rule = _create_rule(admin_session, {**_VALID_RULE, "name": "high-ram", "metricType": "ram"})
    assert rule["metricType"] == "ram"


def test_create_rule_with_group_target(admin_session):
    import uuid
    group_id = str(uuid.uuid4())
    rule = _create_rule(admin_session, {
        **_VALID_RULE,
        "name": "group-target-rule",
        "target": {"type": "group", "value": group_id},
    })
    assert rule["targetType"] == "group"
    assert rule["targetValue"] == group_id


def test_create_rule_with_less_than_operator(admin_session):
    rule = _create_rule(admin_session, {**_VALID_RULE, "name": "low-cpu", "operator": "<", "threshold": 10.0})
    assert rule["operator"] == "<"
