DEV_COMPOSE = podman compose \
	--project-name pulse-dev \
	-f deploy/compose.yaml \
	--env-file deploy/.env.dev

PROD_COMPOSE = podman compose \
	--project-name pulse-prod \
	-f deploy/compose.yaml \
	-f deploy/compose.prod.yaml \
	--env-file deploy/.env.prod

E2E_COMPOSE = JAVA_HOME=/usr/lib/jvm/java-21-openjdk podman compose \
	-f deploy/compose.yaml \
	-f deploy/compose.e2e.yaml \
	--env-file deploy/.env.e2e \
	--project-name pulse-e2e

PODMAN_SOCKET := $(or $(wildcard /run/user/1000/podman/podman.sock),/run/podman/podman.sock)

.PHONY: dev dev-build dev-down dev-logs prod prod-build prod-down prod-logs \
        e2e e2e-logs e2e-up e2e-down tests tests-unit tests-it

dev:
	$(DEV_COMPOSE) up

dev-build:
	$(DEV_COMPOSE) build

dev-down:
	$(DEV_COMPOSE) down

dev-logs:
	$(DEV_COMPOSE) logs -f $(service)

prod:
	$(PROD_COMPOSE) up

prod-build:
	$(PROD_COMPOSE) build

prod-down:
	$(PROD_COMPOSE) down

prod-logs:
	$(PROD_COMPOSE) logs -f $(service)

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
	@set -e; \
	trap 'echo "Stopping containers..."; $(E2E_COMPOSE) down -v; podman image prune -f' EXIT; \
	(cd e2e && set -a && . ../deploy/.env.e2e && set +a && python -m pytest tests/ -v --logs)

e2e-up:
	$(E2E_COMPOSE) up --pull=never -d

e2e-down:
	$(E2E_COMPOSE) down -v
	podman image prune -f

e2e-logs:
	$(E2E_COMPOSE) logs -f
