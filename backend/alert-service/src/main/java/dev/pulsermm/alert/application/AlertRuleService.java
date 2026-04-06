package dev.pulsermm.alert.application;

import dev.pulsermm.alert.api.dto.CreateAlertRuleRequest;
import dev.pulsermm.alert.domain.AlertRule;
import dev.pulsermm.alert.infrastructure.persistence.AlertRuleRepository;
import dev.pulsermm.common.audit.Auditable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AlertRuleService {

    private final AlertRuleRepository ruleRepository;

    public AlertRuleService(AlertRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Auditable(action = "alert_rule.create", permission = "alert:manage")
    public AlertRule create(CreateAlertRuleRequest request, UUID createdBy, UUID orgId) {
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
        rule.setOrgId(orgId);
        return ruleRepository.save(rule);
    }

    @Auditable(action = "alert_rule.create", permission = "alert:manage")
    public AlertRule create(CreateAlertRuleRequest request, UUID createdBy) {
        return create(request, createdBy, null);
    }

    public List<AlertRule> list(UUID orgId) {
        if (orgId == null) return ruleRepository.findAll();
        return ruleRepository.findByOrgId(orgId);
    }

    public List<AlertRule> list() {
        return list(null);
    }

    @Auditable(action = "alert_rule.delete", permission = "alert:manage")
    public void delete(UUID id, UUID callerOrgId) {
        var rule = ruleRepository.findById(id)
            .orElseThrow(() -> new AlertRuleNotFoundException(id));
        if (callerOrgId != null && !callerOrgId.equals(rule.getOrgId())) {
            throw new AlertRuleNotFoundException(id);
        }
        ruleRepository.delete(rule);
    }

    @Auditable(action = "alert_rule.delete", permission = "alert:manage")
    public void delete(UUID id) {
        delete(id, null);
    }
}
