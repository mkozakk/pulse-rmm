import os

BASE_URL = os.getenv("PULSE_API_URL", "http://localhost:8081")

# Keycloak owns auth now. The e2e admin user (with this fixed id) ships in the
# realm export (deploy/keycloak-realm-export.json) and is seeded the Admin role
# in rbac-service (V002__rbac_seed.sql) — keep all three in sync.
# e2e publishes Keycloak on 8181 (dev uses 8180) so both stacks can run at once
# KEYCLOAK_URL in .env.e2e is the container-internal address used by Spring services.
# Pytest runs on the host and needs the host-published port via KEYCLOAK_TEST_URL.
KEYCLOAK_URL = os.getenv("KEYCLOAK_TEST_URL", "http://localhost:8181")
KEYCLOAK_REALM = "pulse-rmm"
KEYCLOAK_E2E_CLIENT = "pulse-e2e"
ADMIN_USERNAME = "admin"
ADMIN_PASSWORD = "admin"
ADMIN_ID = "11111111-1111-1111-1111-111111111111"

AGENT_IMAGE = os.getenv("PULSE_AGENT_IMAGE", "pulse-e2e-agent-e2e")
# Network created by: podman compose --project-name pulse-e2e
E2E_NETWORK = os.getenv("PULSE_E2E_NETWORK", "pulse-e2e_default")
