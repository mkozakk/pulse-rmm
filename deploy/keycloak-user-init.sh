#!/bin/sh
set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://keycloak:8080}"
REALM="pulse-rmm"
RBAC_URL="${RBAC_SERVICE_URL:-http://rbac-service:8083}"

echo "==> Getting admin token from Keycloak master realm..."
TOKEN=$(curl -sf \
  -d "client_id=admin-cli" \
  -d "username=${KEYCLOAK_ADMIN}" \
  -d "password=${KEYCLOAK_ADMIN_PASSWORD}" \
  -d "grant_type=password" \
  "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" | jq -r '.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "ERROR: Failed to get admin token" >&2
  exit 1
fi

echo "==> Checking if '${ADMIN_USERNAME}' already exists in realm '${REALM}'..."
EXISTING=$(curl -sf \
  -H "Authorization: Bearer ${TOKEN}" \
  "${KEYCLOAK_URL}/admin/realms/${REALM}/users?username=${ADMIN_USERNAME}&exact=true")

USER_COUNT=$(echo "$EXISTING" | jq 'length')

if [ "$USER_COUNT" -gt 0 ]; then
  USER_ID=$(echo "$EXISTING" | jq -r '.[0].id')
  echo "==> User '${ADMIN_USERNAME}' already exists (id=${USER_ID}), skipping creation."
else
  echo "==> Creating user '${ADMIN_USERNAME}'..."
  curl -sf -X POST \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{
      \"username\": \"${ADMIN_USERNAME}\",
      \"email\": \"${ADMIN_USERNAME}@pulse.local\",
      \"firstName\": \"Admin\",
      \"lastName\": \"User\",
      \"enabled\": true,
      \"emailVerified\": true,
      \"credentials\": [{\"type\": \"password\", \"value\": \"${ADMIN_PASSWORD}\", \"temporary\": false}]
    }" \
    "${KEYCLOAK_URL}/admin/realms/${REALM}/users"

  USER_ID=$(curl -sf \
    -H "Authorization: Bearer ${TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/${REALM}/users?username=${ADMIN_USERNAME}&exact=true" | jq -r '.[0].id')

  echo "==> Created user with id=${USER_ID}"
fi

echo "==> Assigning Admin role in rbac-service for user ${USER_ID}..."
HTTP_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -H "X-Internal-Token: ${INTERNAL_API_SECRET}" \
  -d "{\"roleName\": \"Admin\"}" \
  "${RBAC_URL}/internal/rbac/users/${USER_ID}/roles" || echo "000")

if [ "$HTTP_STATUS" = "200" ]; then
  echo "==> Admin role assigned (or already assigned)."
else
  echo "WARNING: Role assign returned HTTP ${HTTP_STATUS} — may already be set."
fi

echo "==> Done."
