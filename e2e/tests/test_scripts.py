import pytest
import requests

from config import BASE_URL
from conftest import poll_until

pytestmark = pytest.mark.fast

FAKE_ENDPOINT_ID = "00000000-0000-0000-0000-000000000001"
FAKE_RUN_ID = "00000000-0000-0000-0000-000000000099"


# --- helpers ------------------------------------------------------------------

def _create_script(session, name="test-script", body='echo "hello"'):
    r = session.post(f"{BASE_URL}/api/scripts", json={"name": name, "body": body})
    assert r.status_code == 201, r.text
    return r.json()["id"]


def _approve_script(session, script_id):
    r = session.post(f"{BASE_URL}/api/scripts/{script_id}/approve")
    assert r.status_code == 200, r.text
    return r.json()


# --- script CRUD and listing --------------------------------------------------
# Auth enforcement tests consolidated in test_auth_enforcement.py

def test_create_script_and_get_details(admin_session):
    """Create a script and verify details via GET."""
    r = admin_session.post(
        f"{BASE_URL}/api/scripts",
        json={"name": "crud-test-script", "body": 'echo "crud"'},
    )
    assert r.status_code == 201, r.text
    script_id = r.json()["id"]

    get_r = admin_session.get(f"{BASE_URL}/api/scripts/{script_id}")
    assert get_r.status_code == 200, get_r.text
    data = get_r.json()
    assert data["id"] == script_id
    assert data["name"] == "crud-test-script"
    assert data["approved"] is False
    assert "createdBy" in data
    assert "createdAt" in data


def test_create_script_validation(admin_session):
    """Empty script name/body should be rejected."""
    r = admin_session.post(f"{BASE_URL}/api/scripts", json={"name": "", "body": ""})
    assert r.status_code == 400, r.text


def test_get_script_not_found(admin_session):
    """Getting a non-existent script returns 404."""
    r = admin_session.get(f"{BASE_URL}/api/scripts/{FAKE_RUN_ID}")
    assert r.status_code == 404, r.text


def test_list_scripts_with_status_filters(admin_session):
    """List endpoint filters scripts by approval status."""
    # Create one unapproved and one approved script
    unapproved_id = _create_script(admin_session, name="list-unapproved-script")
    approved_id = _create_script(admin_session, name="list-approved-script")
    _approve_script(admin_session, approved_id)

    # Verify all status shows both
    all_r = admin_session.get(f"{BASE_URL}/api/scripts?status=all")
    assert all_r.status_code == 200, all_r.text
    all_ids = [s["id"] for s in all_r.json()["scripts"]]
    assert unapproved_id in all_ids and approved_id in all_ids

    # Verify pending filter shows only unapproved
    pending_r = admin_session.get(f"{BASE_URL}/api/scripts?status=pending")
    assert pending_r.status_code == 200, pending_r.text
    pending = pending_r.json()["scripts"]
    assert all(not s["approved"] for s in pending)
    assert unapproved_id in [s["id"] for s in pending]

    # Verify library filter shows only approved
    library_r = admin_session.get(f"{BASE_URL}/api/scripts?status=library")
    assert library_r.status_code == 200, library_r.text
    library = library_r.json()["scripts"]
    assert all(s["approved"] for s in library)
    assert approved_id in [s["id"] for s in library]


# --- approval workflow --------------------------------------------------------

def test_approve_script_and_idempotency(admin_session):
    """Approving a script marks it approved; approving again returns 409."""
    script_id = _create_script(admin_session, name="approval-workflow-script")

    # First approval succeeds
    approve_r = admin_session.post(f"{BASE_URL}/api/scripts/{script_id}/approve")
    assert approve_r.status_code == 200, approve_r.text
    assert approve_r.json()["approved"] is True

    # Second approval returns 409 (already approved)
    duplicate_r = admin_session.post(f"{BASE_URL}/api/scripts/{script_id}/approve")
    assert duplicate_r.status_code == 409, duplicate_r.text


def test_approve_script_not_found(admin_session):
    """Approving a non-existent script returns 404."""
    r = admin_session.post(f"{BASE_URL}/api/scripts/{FAKE_RUN_ID}/approve")
    assert r.status_code == 404, r.text


# --- script execution and results -----------------------------------------------

def test_run_script_initiates_and_shows_pending(admin_session):
    """Running a script returns 202 and shows pending results."""
    script_id = _create_script(admin_session, name="run-initiate-script")

    # Initiate run
    run_r = admin_session.post(
        f"{BASE_URL}/api/scripts/{script_id}/run",
        json={"endpointIds": [FAKE_ENDPOINT_ID]},
    )
    assert run_r.status_code == 202, run_r.text
    run_id = run_r.json()["runId"]
    assert run_id

    # Check results show as pending
    results_r = admin_session.get(f"{BASE_URL}/api/scripts/runs/{run_id}/results")
    assert results_r.status_code == 200, results_r.text
    data = results_r.json()
    assert data["runId"] == run_id
    assert data["pending"] == 1
    assert len(data["results"]) == 1
    assert data["results"][0]["pending"] is True
    assert data["results"][0]["exitCode"] is None


def test_run_script_validation(admin_session):
    """Running with empty endpoint list returns 400; non-existent script returns 404."""
    script_id = _create_script(admin_session, name="run-validate-script")

    # Empty endpoint list
    empty_r = admin_session.post(
        f"{BASE_URL}/api/scripts/{script_id}/run",
        json={"endpointIds": []},
    )
    assert empty_r.status_code == 400, empty_r.text

    # Non-existent script
    notfound_r = admin_session.post(
        f"{BASE_URL}/api/scripts/{FAKE_RUN_ID}/run",
        json={"endpointIds": [FAKE_ENDPOINT_ID]},
    )
    assert notfound_r.status_code == 404, notfound_r.text


def test_ack_updates_results_and_requires_no_auth(admin_session):
    """Ack endpoint updates results and is permit-all (no auth required)."""
    script_id = _create_script(admin_session, name="ack-workflow-script")
    run_r = admin_session.post(
        f"{BASE_URL}/api/scripts/{script_id}/run",
        json={"endpointIds": [FAKE_ENDPOINT_ID]},
    )
    run_id = run_r.json()["runId"]

    # Ack without auth succeeds
    ack_r = requests.post(
        f"{BASE_URL}/api/scripts/runs/{run_id}/endpoints/{FAKE_ENDPOINT_ID}/ack",
        json={"exitCode": 0, "output": "execution complete"},
    )
    assert ack_r.status_code == 204, ack_r.text

    # Results updated
    results_r = admin_session.get(f"{BASE_URL}/api/scripts/runs/{run_id}/results")
    data = results_r.json()
    assert data["pending"] == 0
    result = data["results"][0]
    assert result["exitCode"] == 0
    assert "execution complete" in result["output"]
    assert result["pending"] is False


def test_run_results_not_found(admin_session):
    """Getting results for non-existent run returns 404."""
    r = admin_session.get(f"{BASE_URL}/api/scripts/runs/{FAKE_RUN_ID}/results")
    assert r.status_code == 404, r.text


# --- secrets ------------------------------------------------------------------

def test_run_with_secrets_not_returned_in_response(admin_session):
    """Secrets submitted in run body must not appear in results responses."""
    script_id = _create_script(admin_session, name="secrets-test-script")
    secret_value = "super-secret-password-hunter2"

    r = admin_session.post(
        f"{BASE_URL}/api/scripts/{script_id}/run",
        json={
            "endpointIds": [FAKE_ENDPOINT_ID],
            "secrets": {"DB_PASSWORD": secret_value},
        },
    )
    assert r.status_code == 202, r.text
    run_id = r.json()["runId"]
    assert secret_value not in r.text

    results_r = admin_session.get(f"{BASE_URL}/api/scripts/runs/{run_id}/results")
    assert secret_value not in results_r.text


# --- end-to-end with real agent -----------------------------------------------

@pytest.mark.slow
@pytest.mark.requires_agent
def test_linux_script_execution(admin_session, enrolled_agent):
    """Agent receives script command, executes bash, output captured and acked."""
    script_id = _create_script(
        admin_session,
        name="linux-exec-script",
        body='echo "linux-exec-ok"',
    )

    r = admin_session.post(
        f"{BASE_URL}/api/scripts/{script_id}/run",
        json={"endpointIds": [enrolled_agent]},
    )
    assert r.status_code == 202, r.text
    run_id = r.json()["runId"]

    def results_complete():
        r = admin_session.get(f"{BASE_URL}/api/scripts/runs/{run_id}/results")
        assert r.status_code == 200
        data = r.json()
        return data if data["pending"] == 0 else None

    data = poll_until(results_complete, timeout=30)
    result = data["results"][0]
    assert result["exitCode"] == 0
    assert "linux-exec-ok" in result["output"]


@pytest.mark.slow
@pytest.mark.requires_agent
def test_script_execution_permission(admin_session, enrolled_agent):
    """Unapproved script can be run by admin (script:adhoc); endpoint receives and acks it."""
    unapproved_id = _create_script(admin_session, name="perm-unapproved-script", body="echo perm-ok")

    r = admin_session.post(
        f"{BASE_URL}/api/scripts/{unapproved_id}/run",
        json={"endpointIds": [enrolled_agent]},
    )
    assert r.status_code == 202, r.text
    run_id = r.json()["runId"]

    def results_complete():
        r = admin_session.get(f"{BASE_URL}/api/scripts/runs/{run_id}/results")
        data = r.json()
        return data if data["pending"] == 0 else None

    data = poll_until(results_complete, timeout=30)
    assert data["results"][0]["exitCode"] == 0

    # unauthenticated caller cannot run scripts
    r = requests.post(
        f"{BASE_URL}/api/scripts/{unapproved_id}/run",
        json={"endpointIds": [enrolled_agent]},
    )
    assert r.status_code in (401, 403)


@pytest.mark.skip(reason="no Windows agent in CI stack")
def test_windows_script_execution(admin_session, enrolled_agent):
    """Agent executes PowerShell script on Windows and captures output."""
