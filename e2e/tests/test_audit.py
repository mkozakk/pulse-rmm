import time

import pytest
import requests

from config import BASE_URL

pytestmark = pytest.mark.fast

_RULE = {
    "name": "audit test rule",
    "metricType": "cpu",
    "operator": ">",
    "threshold": 90.0,
    "durationSecs": 300,
    "target": {"type": "tag", "value": "env=prod"},
}


def _create_rule(session):
    r = session.post(f"{BASE_URL}/api/alert-rules", json=_RULE)
    assert r.status_code == 201
    return r.json()["id"]


def _delete_rule(session, rule_id):
    session.delete(f"{BASE_URL}/api/alert-rules/{rule_id}")


def test_audit_list_workflow(admin_session, registered_user):
    rule_ids = [_create_rule(admin_session) for _ in range(3)]

    try:
        time.sleep(2)

        r = admin_session.get(f"{BASE_URL}/api/audit")
        assert r.status_code == 200
        data = r.json()
        actions = [e["action"] for e in data["content"]]
        assert actions.count("alert_rule.create") >= 3

        user_id = registered_user["id"]
        r = admin_session.get(f"{BASE_URL}/api/audit?user={user_id}")
        assert r.status_code == 200
        for event in r.json()["content"]:
            assert event["userId"] == user_id

        r = admin_session.delete(f"{BASE_URL}/api/audit")
        assert r.status_code == 403

        r = requests.get(f"{BASE_URL}/api/audit")
        assert r.status_code in (401, 403)

    finally:
        for rid in rule_ids:
            _delete_rule(admin_session, rid)


def test_audit_export_workflow(admin_session):
    rule_ids = [_create_rule(admin_session) for _ in range(2)]

    try:
        time.sleep(2)

        r = admin_session.get(f"{BASE_URL}/api/audit/export?format=csv")
        assert r.status_code == 200
        assert "text/csv" in r.headers.get("Content-Type", "")
        lines = r.text.strip().splitlines()
        assert lines[0].startswith("id,")
        assert len(lines) >= 3  # header + at least 2 records

        r = admin_session.get(f"{BASE_URL}/api/audit/export?format=json")
        assert r.status_code == 200
        assert "ndjson" in r.headers.get("Content-Type", "")
        for line in r.text.strip().splitlines():
            import json
            obj = json.loads(line)
            assert "id" in obj
            assert "action" in obj

    finally:
        for rid in rule_ids:
            _delete_rule(admin_session, rid)
