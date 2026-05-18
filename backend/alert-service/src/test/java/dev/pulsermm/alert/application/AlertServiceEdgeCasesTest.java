package dev.pulsermm.alert.application;

import dev.pulsermm.alert.api.dto.CreateAlertRuleRequest;
import dev.pulsermm.alert.domain.AlertRule;
import dev.pulsermm.alert.infrastructure.persistence.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertServiceEdgeCasesTest {

    private AlertRuleRepository ruleRepo;
    private AlertRuleService ruleService;

    @BeforeEach
    void setUp() {
        ruleRepo = mock(AlertRuleRepository.class);
        ruleService = new AlertRuleService(ruleRepo);
    }

    @Test
    void createRuleSavesAllRequestFields() {
        UUID userId = UUID.randomUUID();
        CreateAlertRuleRequest req = new CreateAlertRuleRequest(
            "High CPU", "cpu", ">", 80.0, 300, new CreateAlertRuleRequest.TargetSpec("group", "default")
        );

        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = ruleService.create(req, userId);

        assertThat(result.getName()).isEqualTo("High CPU");
        assertThat(result.getMetricType()).isEqualTo("cpu");
        assertThat(result.getThreshold()).isEqualTo(80.0);
        assertThat(result.getDurationSecs()).isEqualTo(300);
    }

    @Test
    void listRulesReturnsEmptyList() {
        when(ruleRepo.findAll()).thenReturn(List.of());

        var result = ruleService.list();

        assertThat(result).isEmpty();
    }

    @Test
    void deleteRuleNotFoundThrows() {
        UUID ruleId = UUID.randomUUID();
        when(ruleRepo.existsById(ruleId)).thenReturn(false);

        assertThatThrownBy(() -> ruleService.delete(ruleId))
            .isInstanceOf(AlertRuleNotFoundException.class);
    }
}
