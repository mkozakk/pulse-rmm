package dev.pulsermm.alert.application;

import dev.pulsermm.alert.domain.AlertEvent;
import dev.pulsermm.alert.domain.AlertRule;
import dev.pulsermm.alert.infrastructure.EndpointResolver;
import dev.pulsermm.alert.infrastructure.MetricQueryGateway;
import dev.pulsermm.alert.infrastructure.persistence.AlertEventRepository;
import dev.pulsermm.alert.infrastructure.persistence.AlertRuleRepository;
import dev.pulsermm.common.events.DomainEvent;
import dev.pulsermm.common.events.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import java.util.List;
import java.util.UUID;

@Component
public class AlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluator.class);

    private final AlertRuleRepository ruleRepository;
    private final AlertEventRepository eventRepository;
    private final EndpointResolver endpointResolver;
    private final MetricQueryGateway metricQueryGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final DomainEventPublisher domainEventPublisher;

    public AlertEvaluator(AlertRuleRepository ruleRepository,
                          AlertEventRepository eventRepository,
                          EndpointResolver endpointResolver,
                          MetricQueryGateway metricQueryGateway,
                          ApplicationEventPublisher eventPublisher,
                          DomainEventPublisher domainEventPublisher) {
        this.ruleRepository = ruleRepository;
        this.eventRepository = eventRepository;
        this.endpointResolver = endpointResolver;
        this.metricQueryGateway = metricQueryGateway;
        this.eventPublisher = eventPublisher;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void evaluate() {
        List<AlertRule> rules = ruleRepository.findAllByEnabledTrue();
        log.debug("Evaluating {} alert rules", rules.size());

        for (AlertRule rule : rules) {
            List<UUID> endpoints = endpointResolver.resolve(rule.getTargetType(), rule.getTargetValue());
            for (UUID endpointId : endpoints) {
                evaluateRuleForEndpoint(rule, endpointId);
            }
        }
    }

    private void evaluateRuleForEndpoint(AlertRule rule, UUID endpointId) {
        boolean holds = metricQueryGateway.conditionHolds(
                endpointId, rule.getMetricType(), rule.getOperator(), rule.getThreshold(), rule.getDurationSecs());

        if (holds) {
            // Skip if there's already an open event
            if (eventRepository.findOpenEvent(rule.getId(), endpointId).isPresent()) {
                return;
            }
            // Skip if acked but condition hasn't cleared yet (must re-trigger after clear)
            if (eventRepository.existsAckedButNotCleared(rule.getId(), endpointId)) {
                return;
            }
            tryInsertEvent(rule, endpointId);
        } else {
            boolean cleared = metricQueryGateway.conditionCleared(
                    endpointId, rule.getMetricType(), rule.getOperator(), rule.getThreshold(), rule.getDurationSecs());
            if (cleared) {
                eventRepository.markCleared(rule.getId(), endpointId);
            }
        }
    }

    private void tryInsertEvent(AlertRule rule, UUID endpointId) {
        try {
            var event = eventRepository.save(new AlertEvent(rule, endpointId));
            log.info("Alert fired: rule={} endpoint={}", rule.getId(), endpointId);
            eventPublisher.publishEvent(new AlertFiredEvent(this, event));
            domainEventPublisher.publish(DomainEvent.of("alert.fired", Map.of(
                "alertEventId", event.getId().toString(),
                "ruleId", rule.getId().toString(),
                "endpointId", endpointId.toString(),
                "metricType", rule.getMetricType(),
                "threshold", rule.getThreshold()
            )));
        } catch (DataIntegrityViolationException e) {
            // Unique constraint on (rule_id, endpoint_id) WHERE acked_at IS NULL — race condition, safe to ignore
            log.debug("Alert event already exists for rule={} endpoint={}", rule.getId(), endpointId);
        }
    }
}
