import os

BASE_URL = os.getenv("PULSE_API_URL", "http://localhost:8081")

# Keycloak owns auth now. Admin user is created by keycloak-user-init at stack startup.
# e2e publishes Keycloak on 8181 (dev uses 8180) so both stacks can run at once
# KEYCLOAK_URL in .env.e2e is the container-internal address used by Spring services.
# Pytest runs on the host and needs the host-published port via KEYCLOAK_TEST_URL.
KEYCLOAK_URL = os.getenv("KEYCLOAK_TEST_URL", "http://localhost:8181")
KEYCLOAK_REALM = "pulse-rmm"
KEYCLOAK_E2E_CLIENT = "pulse-e2e"
ADMIN_USERNAME = os.getenv("ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "admin")
# ADMIN_ID is no longer fixed — the init script creates the user dynamically.
# Tests that need the admin's UUID must look it up from Keycloak.
ADMIN_ID = os.getenv("ADMIN_ID", "")

AGENT_IMAGE = os.getenv("PULSE_AGENT_IMAGE", "pulse-e2e-agent-e2e")
# Network created by: podman compose --project-name pulse-e2e
E2E_NETWORK = os.getenv("PULSE_E2E_NETWORK", "pulse-e2e_default")
