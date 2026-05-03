import requests

from config import BASE_URL


def test_set_tags_requires_auth(enrolled_agent):
    r = requests.put(
        f"{BASE_URL}/api/endpoints/{enrolled_agent}/tags",
        json={"tags": [{"key": "env", "value": "e2e"}]},
    )
    assert r.status_code in (401, 403)


def test_set_tags_filters_endpoint_list(admin_session, enrolled_agent):
    tag = {"key": "env", "value": "e2e"}
    r = admin_session.put(
        f"{BASE_URL}/api/endpoints/{enrolled_agent}/tags",
        json={"tags": [tag]},
    )
    assert r.status_code == 200, r.text

    filtered = admin_session.get(f"{BASE_URL}/api/endpoints", params={"tag": "env=e2e"})
    assert filtered.status_code == 200, filtered.text
    ids = [e["id"] for e in filtered.json()]
    assert enrolled_agent in ids


def test_remove_tag_via_replace_all(admin_session, enrolled_agent):
    r = admin_session.put(
        f"{BASE_URL}/api/endpoints/{enrolled_agent}/tags",
        json={"tags": []},
    )
    assert r.status_code == 200, r.text

    filtered = admin_session.get(f"{BASE_URL}/api/endpoints", params={"tag": "env=e2e"})
    assert filtered.status_code == 200, filtered.text
    ids = [e["id"] for e in filtered.json()]
    assert enrolled_agent not in ids
