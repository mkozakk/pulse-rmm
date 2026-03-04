import pytest
import requests

from config import BASE_URL

pytestmark = pytest.mark.fast


def _create_group_and_token(admin_session):
    group_r = admin_session.post(f"{BASE_URL}/api/groups", json={"name": "InstallScriptGroup", "parentId": None})
    assert group_r.status_code == 201
    group_id = group_r.json()["id"]

    token_r = admin_session.post(f"{BASE_URL}/api/enrolment/tokens", json={"groupId": group_id, "ttlHours": 24})
    assert token_r.status_code == 201
    return token_r.json()["id"]


def test_install_script_body(admin_session):
    token_id = _create_group_and_token(admin_session)

    r = requests.get(f"{BASE_URL}/install/{token_id}.sh")
    assert r.status_code == 200
    body = r.text
    assert body.startswith("#!/usr/bin/env bash")
    assert token_id in body
    assert "localhost:8080" in body or "PULSE_API_URL" in body
    assert "enrolment_token:" in body


def test_install_script_invalid_token():
    r = requests.get(f"{BASE_URL}/install/nonexistent.sh")
    assert r.status_code == 404


def test_install_script_random_uuid():
    r = requests.get(f"{BASE_URL}/install/00000000-0000-0000-0000-000000000000.sh")
    assert r.status_code == 404
