import pytest
import time

from config import BASE_URL
from conftest import poll_until

pytestmark = [pytest.mark.slow, pytest.mark.requires_agent]


def test_software_workflow(admin_session, enrolled_agent):
    endpoint_id = enrolled_agent
    base = f"{BASE_URL}/api/endpoints/{endpoint_id}/software"

    # Wait for agent's initial software scan to complete and push list
    def initial_software_list_populated():
        r = admin_session.get(base)
        assert r.status_code == 200
        packages = r.json()
        return len(packages) > 0

    poll_until(initial_software_list_populated, timeout=30)

    r = admin_session.get(base)
    initial_list = r.json()
    print(f"\n[test] initial software list has {len(initial_list)} packages")

    # Request to install curl
    r = admin_session.post(f"{base}/install", json={"name": "curl", "version": ""})
    assert r.status_code == 201, r.text
    cmd_id = r.json()['id']
    print(f"[test] install command created: {cmd_id}")

    # Wait for agent to execute install and push updated software list
    time.sleep(5)
    print("[test] waited 5s for agent to execute install")

    # Verify the software list endpoint still works
    def software_list_available():
        r = admin_session.get(base)
        assert r.status_code == 200
        packages = r.json()
        return len(packages) > 0

    poll_until(software_list_available, timeout=60)
    print("[test] software list endpoint is working")


@pytest.mark.skip(reason="no windows stack")
def test_software_workflow_windows(admin_session, enrolled_agent):
    pass
