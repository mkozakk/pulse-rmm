import requests

from config import BASE_URL

# Auth enforcement tests consolidated in test_auth_enforcement.py


def test_create_and_list_groups(admin_session):
    name = "E2eGroup"
    create_r = admin_session.post(
        f"{BASE_URL}/api/groups",
        json={"name": name, "parentId": None},
    )
    assert create_r.status_code == 201, create_r.text
    created = create_r.json()
    assert created["id"]
    assert created["name"] == name
    assert created["parentId"] is None

    list_r = admin_session.get(f"{BASE_URL}/api/groups")
    assert list_r.status_code == 200, list_r.text
    ids = [g["id"] for g in list_r.json()]
    assert created["id"] in ids
