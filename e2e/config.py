import os

BASE_URL = os.getenv("PULSE_BASE_URL", "http://localhost:8080")
ADMIN_USERNAME = "e2e_admin"
ADMIN_PASSWORD = "e2eadminpassword1"

AGENT_IMAGE = os.getenv("PULSE_AGENT_IMAGE", "pulse-rmm-agent-e2e")
# Network created by: podman compose --project-name pulse-e2e
E2E_NETWORK = os.getenv("PULSE_E2E_NETWORK", "pulse-e2e_default")
