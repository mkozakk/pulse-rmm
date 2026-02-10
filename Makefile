E2E_COMPOSE = JAVA_HOME=/usr/lib/jvm/java-21-openjdk podman compose \
        -f deploy/compose.yaml \
        -f deploy/compose.e2e.yaml \
        --env-file deploy/.env.e2e \
        --project-name pulse-e2e

.PHONY: e2e e2e-logs

e2e:
	@echo "Cleaning up from previous run..."
	$(E2E_COMPOSE) down -v 2>/dev/null || true
	@echo "Building changed services..."
	$(E2E_COMPOSE) build --pull=false
	@echo "Starting stack..."
	$(E2E_COMPOSE) up --pull=never -d
	@echo "Waiting for gateway..."
	@timeout 60 bash -c 'until curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; do sleep 0.5; done'
	@echo "Running tests..."
	cd e2e && python -m pytest tests/ -v --logs
	@echo "Stopping containers..."
	$(E2E_COMPOSE) down -v

e2e-logs:
	$(E2E_COMPOSE) logs -f
