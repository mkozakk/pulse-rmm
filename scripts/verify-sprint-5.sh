#!/usr/bin/env bash
# Sprint 5 smoke test — runs the stack and prints a manual checklist.
# Automated checks: services healthy, agent connects, endpoint appears in list.
# The terminal I/O itself requires a human (no headless browser).
set -euo pipefail

BASE="http://localhost:8080"
WEBAPP="http://localhost:5173"

echo "==> Starting stack..."
podman compose -f deploy/compose.yaml up -d --build

echo "==> Waiting for api-gateway..."
until curl -sf "$BASE/actuator/health" >/dev/null; do sleep 1; done
echo "    gateway healthy"

echo "==> Waiting for Keycloak..."
until curl -sf "http://localhost:8180/realms/pulse-rmm" >/dev/null; do sleep 1; done

echo "==> Getting admin token from Keycloak..."
TOKEN=$(curl -sf -X POST "http://localhost:8180/realms/pulse-rmm/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=pulse-e2e" \
  -d "username=admin" -d "password=admin" | jq -r .access_token)
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || { echo "ERROR: token request failed"; exit 1; }
echo "    got access token"

echo "==> Creating enrolment token..."
# groupId must be a valid UUID; ttlHours must be positive
INV=$(curl -sf -X POST "$BASE/api/enrolment/tokens" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"groupId":"00000000-0000-0000-0000-000000000001","ttlHours":24}' | jq -r .id)
[ -n "$INV" ] || { echo "ERROR: token creation failed"; exit 1; }
echo "    enrolment token: $INV"

echo "==> Building agent..."
(cd agent && go build -o pulse-agent .)

echo "==> Starting agent..."
PULSE_TOKEN="$INV" \
PULSE_SERVER="localhost:9091" \
PULSE_METRIC_SERVER="localhost:9092" \
PULSE_GATEWAY="localhost:9090" \
  ./agent/pulse-agent &
AGENT_PID=$!
echo "    agent PID: $AGENT_PID"

echo "==> Waiting for agent to register (3s)..."
sleep 3

echo "==> Fetching endpoint list..."
ENDPOINT_ID=$(curl -sf -H "Authorization: Bearer $TOKEN" "$BASE/api/endpoints" | jq -r '.[0].id')
[ -n "$ENDPOINT_ID" ] && [ "$ENDPOINT_ID" != "null" ] || { echo "ERROR: no endpoints found"; kill $AGENT_PID; exit 1; }
echo "    endpoint ID: $ENDPOINT_ID"

echo ""
echo "============================================================"
echo " AUTOMATED CHECKS PASSED — MANUAL STEPS FOLLOW"
echo "============================================================"
echo ""
echo "1. Open the webapp:"
echo "   $WEBAPP/endpoints/$ENDPOINT_ID"
echo "   -> click 'Open Terminal'"
echo "   -> terminal pane should appear within 1s"
echo ""
echo "2. Type in the terminal:"
echo "   echo sprint5"
echo "   -> output 'sprint5' should appear within 200ms"
echo ""
echo "3. Resize the browser window"
echo "   -> terminal should reflow; run 'tput cols' to verify new width"
echo ""
echo "4. Type 'exit'"
echo "   -> pane shows '[session closed]'"
echo "   -> no orphan bash process (check: ps aux | grep bash)"
echo ""
echo "5. Log out, create a junior_technician user (or seed via SQL),"
echo "   log in as that user, navigate to:"
echo "   $WEBAPP/endpoints/$ENDPOINT_ID/shell"
echo "   -> connection should close with policy violation (403)"
echo ""
echo "6. Press Ctrl-C here to stop the agent, then try opening"
echo "   the terminal page again -> should show 'endpoint offline'"
echo ""
echo "7. (Windows VM) Run agent with PULSE_GATEWAY=<host>:9090,"
echo "   open terminal, type 'Get-Process', confirm output."
echo ""
echo "============================================================"
echo " Press Ctrl-C to stop the agent when done."
echo "============================================================"

wait $AGENT_PID || true
echo ""
echo "Agent stopped. Re-check that 'endpoint offline' shows in webapp."
