E2E_COMPOSE = JAVA_HOME=/usr/lib/jvm/java-21-openjdk podman compose \
        -f deploy/compose.yaml \
        -f deploy/compose.e2e.yaml \
        --env-file deploy/.env.e2e \
        --project-name pulse-e2e

.PHONY: e2e e2e-up e2e-down e2e-logs e2e-agent-build e2e-build

e2e-agent-build:
	podman build --pull=false -t pulse-rmm-agent-e2e -f agent/Dockerfile .

e2e-build: e2e-agent-build
	$(E2E_COMPOSE) build --pull=false

e2e-up:
	$(E2E_COMPOSE) up -d
	@echo "Waiting for service health..."
	@until curl -sf http://localhost:8083/actuator/health >/dev/null 2>&1; do sleep 1; done
	@until curl -sf http://localhost:8081/actuator/health >/dev/null 2>&1; do sleep 1; done
	@until curl -sf http://localhost:8082/actuator/health >/dev/null 2>&1; do sleep 1; done
	@until curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; do sleep 1; done
	@echo "Stack ready."

e2e-down:
	$(E2E_COMPOSE) down -v

e2e-logs:
	$(E2E_COMPOSE) logs -f

e2e:
	$(MAKE) e2e-down
	$(MAKE) e2e-build
	$(MAKE) e2e-up
	cd e2e && python -m pytest tests/ -v --logs
	$(MAKE) e2e-downe
