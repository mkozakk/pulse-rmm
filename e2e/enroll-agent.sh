#!/bin/bash
set -e

# This script enrolls an agent container in the e2e tests.
# Usage: ./enroll-agent.sh <container_name> <api_url> <token>

CONTAINER_NAME="${1:?Container name required}"
API_URL="${2:?API URL required}"
ENROLMENT_TOKEN="${3:?Enrolment token required}"

echo "[enroll] Writing config to $CONTAINER_NAME..."
podman exec "$CONTAINER_NAME" bash -c "cat > /etc/pulse-agent/config.yaml <<'EOF'
api_url: $API_URL
grpc_addr: 127.0.0.1:9090
enrolment_token: $ENROLMENT_TOKEN
data_dir: /var/lib/pulse-agent
EOF"

echo "[enroll] Config written, agent will enroll on startup..."
