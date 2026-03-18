E2E_COMPOSE = JAVA_HOME=/usr/lib/jvm/java-21-openjdk podman compose \
	-f deploy/compose.yaml \
	-f deploy/compose.e2e.yaml \
	--env-file deploy/.env.e2e \
	--project-name pulse-e2e

PODMAN_SOCKET := $(or $(wildcard /run/user/1000/podman/podman.sock),/run/podman/podman.sock)

.PHONY: e2e e2e-logs e2e-up e2e-down tests tests-unit tests-it

tests: tests-unit tests-it

tests-unit:
	@export JAVA_HOME=/usr/lib/jvm/java-21-openjdk; \
	if [ -z "$(service)" ]; then \
		cd backend && mvn verify -DskipITs; \
	else \
		cd backend && mvn verify -DskipITs -pl $(service); \
	fi

tests-it:
	@if [ ! -e "$(PODMAN_SOCKET)" ]; then \
		echo "Error: Podman socket not found at $(PODMAN_SOCKET)"; \
		exit 1; \
	fi
	@export DOCKER_HOST=unix://$(PODMAN_SOCKET); \
	export JAVA_HOME=/usr/lib/jvm/java-21-openjdk; \
	if [ -z "$(service)" ]; then \
		cd backend && mvn verify -Dsurefire.skip=true; \
	else \
		cd backend && mvn verify -Dsurefire.skip=true -pl $(service); \
	fi

e2e:
	@echo "Cleaning up from previous run..."
	$(E2E_COMPOSE) down -v 2>/dev/null || true
	@echo "Building changed services..."
	$(E2E_COMPOSE) build --force-rm
	@echo "Building agent image..."
	podman build -t pulse-e2e-agent-e2e -f agent/Dockerfile .
	@echo "Starting stack..."
	$(E2E_COMPOSE) up -d
	@echo "Waiting for gateway..."
	@timeout 60 bash -c 'until curl -sf http://localhost:8081/actuator/health >/dev/null 2>&1; do sleep 0.5; done'
	@echo "Running tests..."
	cd e2e && set -a && . ../deploy/.env.e2e && set +a && python -m pytest tests/ -v --logs
	@echo "Stopping containers..."
	$(E2E_COMPOSE) down -v
	@echo "Cleaning dangling images..."
	podman image prune -f

e2e-up:
	$(E2E_COMPOSE) up --pull=never -d

e2e-down:
	$(E2E_COMPOSE) down -v
	podman image prune -f

e2e-logs:
	$(E2E_COMPOSE) logs -f
