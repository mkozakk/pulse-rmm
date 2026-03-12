#!/bin/bash
set -e

CONFIG_FILE="/etc/pulse-agent/config.yaml"

# If config doesn't exist and token is provided via env var, create it
if [[ ! -f "$CONFIG_FILE" ]]; then
  if [[ -n "${ENROLMENT_TOKEN:-}" ]]; then
    echo "[agent] Creating config from environment variables..."
    cat > "$CONFIG_FILE" << EOF
api_url: ${API_URL:-http://localhost:8081}
grpc_addr: ${GRPC_ADDR:-127.0.0.1:9090}
enrolment_token: ${ENROLMENT_TOKEN}
data_dir: ${DATA_DIR:-/var/lib/pulse-agent}
tls_enabled: ${TLS_ENABLED:-false}
EOF
  else
    echo "[agent] Waiting for config file at $CONFIG_FILE..."
    # Wait up to 30 seconds for config to appear (via setup script or volume mount)
    for i in {1..60}; do
      if [[ -f "$CONFIG_FILE" ]]; then
        echo "[agent] Config file found!"
        break
      fi
      sleep 0.5
    done
    if [[ ! -f "$CONFIG_FILE" ]]; then
      echo "[agent] ERROR: Config file not found after 30 seconds" >&2
      exit 1
    fi
  fi
fi

echo "[agent] Using config:"
cat "$CONFIG_FILE"

# Start the agent
exec pulse-agent
