import os

BASE_URL = os.getenv("PULSE_API_URL", "http://localhost:8081")
ADMIN_USERNAME = "e2e_admin"
ADMIN_PASSWORD = "e2eadminpassword1"

AGENT_IMAGE = os.getenv("PULSE_AGENT_IMAGE", "pulse-e2e-agent-e2e")
# Network created by: podman compose --project-name pulse-e2e
E2E_NETWORK = os.getenv("PULSE_E2E_NETWORK", "pulse-e2e_default")
