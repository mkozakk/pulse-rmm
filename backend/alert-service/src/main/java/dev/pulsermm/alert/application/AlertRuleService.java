package dev.pulsermm.alert.application;

import dev.pulsermm.alert.api.dto.CreateAlertRuleRequest;
import dev.pulsermm.alert.domain.AlertRule;
import dev.pulsermm.alert.infrastructure.persistence.AlertRuleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AlertRuleService {

    private final AlertRuleRepository ruleRepository;

    public AlertRuleService(AlertRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public AlertRule create(CreateAlertRuleRequest request, UUID createdBy) {
        var rule = new AlertRule(
            request.name(),
            request.metricType(),
            request.operator(),
            request.threshold(),
            request.durationSecs(),
            request.target().type(),
            request.target().value(),
            createdBy
        );
        return ruleRepository.save(rule);
    }

    public List<AlertRule> list() {
        return ruleRepository.findAll();
    }

    public void delete(UUID id) {
        if (!ruleRepository.existsById(id)) {
            throw new AlertRuleNotFoundException(id);
        }
        ruleRepository.deleteById(id);
    }
}
