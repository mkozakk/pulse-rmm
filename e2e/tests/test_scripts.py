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


# --- auth checks --------------------------------------------------------------

def test_create_script_requires_auth():
    r = requests.post(f"{BASE_URL}/api/scripts", json={"name": "x", "body": "echo x"})
    assert r.status_code in (401, 403)


def test_get_script_requires_auth():
    r = requests.get(f"{BASE_URL}/api/scripts/{FAKE_RUN_ID}")
    assert r.status_code in (401, 403)


def test_list_scripts_requires_auth():
    r = requests.get(f"{BASE_URL}/api/scripts")
    assert r.status_code in (401, 403)


def test_approve_script_requires_auth():
    r = requests.post(f"{BASE_URL}/api/scripts/{FAKE_RUN_ID}/approve")
    assert r.status_code in (401, 403)


def test_run_script_requires_auth():
    r = requests.post(
        f"{BASE_URL}/api/scripts/{FAKE_RUN_ID}/run",
        json={"endpointIds": [FAKE_ENDPOINT_ID]},
    )
    assert r.status_code in (401, 403)


def test_run_results_requires_auth():
    r = requests.get(f"{BASE_URL}/api/scripts/runs/{FAKE_RUN_ID}/results")
    assert r.status_code in (401, 403)


# --- script CRUD --------------------------------------------------------------

def test_create_script(admin_session):
    r = admin_session.post(
        f"{BASE_URL}/api/scripts",
        json={"name": "crud-test-script", "body": 'echo "crud"'},
    )
    assert r.status_code == 201, r.text
    data = r.json()
    assert "id" in data
    assert data["id"]


def test_create_script_validation(admin_session):
    r = admin_session.post(f"{BASE_URL}/api/scripts", json={"name": "", "body": ""})
    assert r.status_code == 400, r.text


def test_get_script(admin_session):
    script_id = _create_script(admin_session, name="get-test-script", body="echo get")

    r = admin_session.get(f"{BASE_URL}/api/scripts/{script_id}")
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["id"] == script_id
    assert data["name"] == "get-test-script"
    assert data["body"] == "echo get"
    assert data["approved"] is False
    assert "createdBy" in data
    assert "createdAt" in data


def test_get_script_not_found(admin_session):
    r = admin_session.get(f"{BASE_URL}/api/scripts/{FAKE_RUN_ID}")
    assert r.status_code == 404, r.text


def test_list_scripts_returns_created(admin_session):
    script_id = _create_script(admin_session, name="list-test-script")

    r = admin_session.get(f"{BASE_URL}/api/scripts?status=all")
    assert r.status_code == 200, r.text
    data = r.json()
    ids = [s["id"] for s in data["scripts"]]
    assert script_id in ids


def test_list_scripts_pending_filter(admin_session):
    script_id = _create_script(admin_session, name="pending-filter-script")

    r = admin_session.get(f"{BASE_URL}/api/scripts?status=pending")
    assert r.status_code == 200, r.text
    scripts = r.json()["scripts"]
    ids = [s["id"] for s in scripts]
    assert script_id in ids

    for s in scripts:
        assert s["approved"] is False


def test_list_scripts_library_filter(admin_session):
    unapproved_id = _create_script(admin_session, name="library-unapproved-script")
    approved_id = _create_script(admin_session, name="library-approved-script")
    _approve_script(admin_session, approved_id)

    r = admin_session.get(f"{BASE_URL}/api/scripts?status=library")
    assert r.status_code == 200, r.text
    scripts = r.json()["scripts"]
    ids = [s["id"] for s in scripts]

    assert approved_id in ids
    assert unapproved_id not in ids
    for s in scripts:
        assert s["approved"] is True


# --- approval workflow --------------------------------------------------------

def test_approve_script(admin_session):
    script_id = _create_script(admin_session, name="to-approve-script")

    r = admin_session.post(f"{BASE_URL}/api/scripts/{script_id}/approve")
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["approved"] is True
    assert data["approvedAt"] is not None

    # verify via get
    get_r = admin_session.get(f"{BASE_URL}/api/scripts/{script_id}")
    assert get_r.json()["approved"] is True


def test_approve_script_not_found(admin_session):
    r = admin_session.post(f"{BASE_URL}/api/scripts/{FAKE_RUN_ID}/approve")
    assert r.status_code == 404, r.text


def test_approve_script_duplicate_returns_409(admin_session):
    script_id = _create_script(admin_session, name="double-approve-script")
    _approve_script(admin_session, script_id)

    r = admin_session.post(f"{BASE_URL}/api/scripts/{script_id}/approve")
    assert r.status_code == 409, r.text


# --- run initiation -----------------------------------------------------------

def test_run_script_returns_202(admin_session):
    script_id = _create_script(admin_session, name="run-202-script")

    r = admin_session.post(
        f"{BASE_URL}/api/scripts/{script_id}/run",
        json={"endpointIds": [FAKE_ENDPOINT_ID]},
    )
    assert r.status_code == 202, r.text
    data = r.json()
    assert "runId" in data
    assert data["runId"]


def test_run_script_not_found(admin_session):
    r = admin_session.post(
        f"{BASE_URL}/api/scripts/{FAKE_RUN_ID}/run",
        json={"endpointIds": [FAKE_ENDPOINT_ID]},
    )
    assert r.status_code == 404, r.text


def test_run_requires_at_least_one_endpoint(admin_session):
    script_id = _create_script(admin_session, name="run-no-endpoints-script")

    r = admin_session.post(
        f"{BASE_URL}/api/scripts/{script_id}/run",
        json={"endpointIds": []},
    )
    assert r.status_code == 400, r.text


# --- results polling ----------------------------------------------------------

def test_run_results_shows_pending(admin_session):
    script_id = _create_script(admin_session, name="results-pending-script")
    r = admin_session.post(
        f"{BASE_URL}/api/scripts/{script_id}/run",
        json={"endpointIds": [FAKE_ENDPOINT_ID]},
    )
    run_id = r.json()["runId"]

    r = admin_session.get(f"{BASE_URL}/api/scripts/runs/{run_id}/results")
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["runId"] == run_id
    assert data["total"] == 1
    assert data["pending"] == 1
    assert len(data["results"]) == 1
    result = data["results"][0]
    assert result["pending"] is True
    assert result["exitCode"] is None


def test_run_results_not_found(admin_session):
    r = admin_session.get(f"{BASE_URL}/api/scripts/runs/{FAKE_RUN_ID}/results")
    assert r.status_code == 404, r.text


def test_ack_updates_result(admin_session):
    script_id = _create_script(admin_session, name="ack-test-script")
    r = admin_session.post(
        f"{BASE_URL}/api/scripts/{script_id}/run",
        json={"endpointIds": [FAKE_ENDPOINT_ID]},
    )
    run_id = r.json()["runId"]

    ack_r = requests.post(
        f"{BASE_URL}/api/scripts/runs/{run_id}/endpoints/{FAKE_ENDPOINT_ID}/ack",
        json={"exitCode": 0, "output": "all done"},
    )
    assert ack_r.status_code == 204, ack_r.text

    results_r = admin_session.get(f"{BASE_URL}/api/scripts/runs/{run_id}/results")
    data = results_r.json()
    assert data["pending"] == 0
    result = data["results"][0]
    assert result["exitCode"] == 0
    assert "all done" in result["output"]
    assert result["pending"] is False


def test_ack_no_auth_required(admin_session):
    """Ack endpoint is permit-all so agents can call it without JWT."""
    script_id = _create_script(admin_session, name="ack-no-auth-script")
    r = admin_session.post(
        f"{BASE_URL}/api/scripts/{script_id}/run",
        json={"endpointIds": [FAKE_ENDPOINT_ID]},
    )
    run_id = r.json()["runId"]

    r = requests.post(
        f"{BASE_URL}/api/scripts/runs/{run_id}/endpoints/{FAKE_ENDPOINT_ID}/ack",
        json={"exitCode": 1, "output": "failed"},
    )
    assert r.status_code == 204, r.text


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
def test_script_execution_end_to_end(admin_session, enrolled_agent):
    print(f"\n[test] running script on enrolled agent {enrolled_agent}")

    script_id = _create_script(
        admin_session,
        name="e2e-exec-script",
        body='echo "e2e-exec-output"',
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
        if data["pending"] == 0:
            return data
        return None

    data = poll_until(results_complete, timeout=30)
    results = data["results"]
    assert len(results) == 1
    assert results[0]["exitCode"] == 0
    assert "e2e-exec-output" in results[0]["output"]


@pytest.mark.slow
@pytest.mark.requires_agent
def test_approved_script_runs_successfully(admin_session, enrolled_agent):
    print(f"\n[test] approved script on enrolled agent {enrolled_agent}")

    script_id = _create_script(admin_session, name="e2e-approved-run", body="echo approved-ok")
    _approve_script(admin_session, script_id)

    r = admin_session.post(
        f"{BASE_URL}/api/scripts/{script_id}/run",
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
    assert "approved-ok" in data["results"][0]["output"]


@pytest.mark.slow
@pytest.mark.requires_agent
def test_bulk_script_execution(admin_session, enrolled_agent):
    """Run a script targeting multiple endpoint IDs; fake endpoints stay pending, real one acks."""
    script_id = _create_script(admin_session, name="e2e-bulk-script", body="echo bulk-ok")

    fake1 = "00000000-0000-0000-0000-000000000011"
    fake2 = "00000000-0000-0000-0000-000000000012"
    endpoint_ids = [enrolled_agent, fake1, fake2]

    r = admin_session.post(
        f"{BASE_URL}/api/scripts/{script_id}/run",
        json={"endpointIds": endpoint_ids},
    )
    assert r.status_code == 202, r.text
    run_id = r.json()["runId"]

    results_r = admin_session.get(f"{BASE_URL}/api/scripts/runs/{run_id}/results")
    assert results_r.json()["total"] == 3

    def real_agent_acked():
        r = admin_session.get(f"{BASE_URL}/api/scripts/runs/{run_id}/results")
        data = r.json()
        for result in data["results"]:
            if result["endpointId"] == enrolled_agent and not result["pending"]:
                return data
        return None

    data = poll_until(real_agent_acked, timeout=30)
    real_result = next(r for r in data["results"] if r["endpointId"] == enrolled_agent)
    assert real_result["exitCode"] == 0
    assert "bulk-ok" in real_result["output"]
    # fake endpoints remain pending
    assert data["pending"] == 2
