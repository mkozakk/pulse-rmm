package dev.pulsermm.alert.application;

import dev.pulsermm.alert.api.dto.CreateAlertRuleRequest;
import dev.pulsermm.alert.domain.AlertRule;
import dev.pulsermm.alert.infrastructure.persistence.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class AlertRuleServiceTest {

    private AlertRuleRepository repository;
    private AlertRuleService service;

    @BeforeEach
    void setUp() {
        repository = mock(AlertRuleRepository.class);
        service = new AlertRuleService(repository);
    }

    @Test
    void createSavesRule() {
        UUID userId = UUID.randomUUID();
        var request = new CreateAlertRuleRequest("High CPU", "cpu", ">", 80.0, 300, new CreateAlertRuleRequest.TargetSpec("group", "prod"));
        var rule = new AlertRule("High CPU", "cpu", ">", 80.0, 300, "group", "prod", userId);

        when(repository.save(org.mockito.ArgumentMatchers.any(AlertRule.class))).thenReturn(rule);

        var result = service.create(request, userId);

        assertThat(result.getName()).isEqualTo("High CPU");
        assertThat(result.getMetricType()).isEqualTo("cpu");
        verify(repository).save(org.mockito.ArgumentMatchers.any(AlertRule.class));
    }

    @Test
    void listReturnsAllRules() {
        var rule1 = new AlertRule("Test1", "cpu", ">", 80.0, 300, "group", "prod", UUID.randomUUID());
        var rule2 = new AlertRule("Test2", "mem", "<", 20.0, 600, "endpoint", "single", UUID.randomUUID());
        when(repository.findAll()).thenReturn(List.of(rule1, rule2));

        var result = service.list();

        assertThat(result).hasSize(2);
    }

    @Test
    void deleteThrowsIfNotFound() {
        UUID ruleId = UUID.randomUUID();
        when(repository.existsById(ruleId)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(ruleId))
            .isInstanceOf(AlertRuleNotFoundException.class);
    }

    @Test
    void deleteRemovesExistingRule() {
        UUID ruleId = UUID.randomUUID();
        when(repository.existsById(ruleId)).thenReturn(true);

        service.delete(ruleId);

        verify(repository).deleteById(ruleId);
    }
}
