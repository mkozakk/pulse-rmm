package dev.pulsermm.alert.application;

import dev.pulsermm.alert.api.dto.CreateAlertRuleRequest;
import dev.pulsermm.alert.domain.AlertEvent;
import dev.pulsermm.alert.domain.AlertRule;
import dev.pulsermm.alert.infrastructure.persistence.AlertEventRepository;
import dev.pulsermm.alert.infrastructure.persistence.AlertRuleRepository;
import dev.pulsermm.common.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertServiceEdgeCasesTest {

    private AlertRuleRepository ruleRepo;
    private AlertEventRepository eventRepo;
    private DomainEventPublisher eventPublisher;
    private AlertRuleService ruleService;
    private AlertEventService eventService;

    @BeforeEach
    void setUp() {
        ruleRepo = mock(AlertRuleRepository.class);
        eventRepo = mock(AlertEventRepository.class);
        eventPublisher = mock(DomainEventPublisher.class);

        ruleService = new AlertRuleService(ruleRepo);
        eventService = new AlertEventService(eventRepo, eventPublisher);
    }

    // AlertRuleService edge cases

    @Test
    void createRuleSavesAllRequestFields() {
        UUID userId = UUID.randomUUID();
        CreateAlertRuleRequest req = new CreateAlertRuleRequest(
            "High CPU", "cpu", "GREATER_THAN", 80.0, 300, new CreateAlertRuleRequest.Target("endpoint", "uuid")
        );

        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = ruleService.create(req, userId);

        assertThat(result.getName()).isEqualTo("High CPU");
        assertThat(result.getMetricType()).isEqualTo("cpu");
        assertThat(result.getOperator()).isEqualTo("GREATER_THAN");
        assertThat(result.getThreshold()).isEqualTo(80.0);
        assertThat(result.getDurationSecs()).isEqualTo(300);
    }

    @Test
    void createRuleWithZeroThreshold() {
        UUID userId = UUID.randomUUID();
        CreateAlertRuleRequest req = new CreateAlertRuleRequest(
            "Zero Threshold", "traffic", "EQUAL", 0.0, 60, new CreateAlertRuleRequest.Target("endpoint", "uuid")
        );

        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = ruleService.create(req, userId);

        assertThat(result.getThreshold()).isEqualTo(0.0);
    }

    @Test
    void createRuleWithNegativeThreshold() {
        UUID userId = UUID.randomUUID();
        CreateAlertRuleRequest req = new CreateAlertRuleRequest(
            "Negative", "load", "LESS_THAN", -5.0, 120, new CreateAlertRuleRequest.Target("group", "groupId")
        );

        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = ruleService.create(req, userId);

        assertThat(result.getThreshold()).isEqualTo(-5.0);
    }

    @Test
    void createRuleWithVeryLargeDuration() {
        UUID userId = UUID.randomUUID();
        CreateAlertRuleRequest req = new CreateAlertRuleRequest(
            "Long Duration", "memory", "GREATER_THAN", 50.0, 86400, new CreateAlertRuleRequest.Target("endpoint", "uuid")
        );

        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = ruleService.create(req, userId);

        assertThat(result.getDurationSecs()).isEqualTo(86400);
    }

    @Test
    void createRuleWithMinimalDuration() {
        UUID userId = UUID.randomUUID();
        CreateAlertRuleRequest req = new CreateAlertRuleRequest(
            "Short Duration", "disk", "LESS_THAN", 10.0, 1, new CreateAlertRuleRequest.Target("endpoint", "uuid")
        );

        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = ruleService.create(req, userId);

        assertThat(result.getDurationSecs()).isEqualTo(1);
    }

    @Test
    void listRulesReturnsEmptyList() {
        when(ruleRepo.findAll()).thenReturn(List.of());

        var result = ruleService.list();

        assertThat(result).isEmpty();
    }

    @Test
    void listRulesReturnsMultipleRules() {
        AlertRule rule1 = new AlertRule("Alert1", "cpu", "GT", 80.0, 300, "endpoint", "ep1", UUID.randomUUID());
        AlertRule rule2 = new AlertRule("Alert2", "memory", "LT", 20.0, 600, "group", "grp1", UUID.randomUUID());

        when(ruleRepo.findAll()).thenReturn(List.of(rule1, rule2));

        var result = ruleService.list();

        assertThat(result).hasSize(2);
    }

    @Test
    void deleteRuleNotFoundThrows() {
        UUID ruleId = UUID.randomUUID();
        when(ruleRepo.existsById(ruleId)).thenReturn(false);

        assertThatThrownBy(() -> ruleService.delete(ruleId))
            .isInstanceOf(AlertRuleNotFoundException.class);
    }

    @Test
    void deleteRuleExistingRemovesIt() {
        UUID ruleId = UUID.randomUUID();
        when(ruleRepo.existsById(ruleId)).thenReturn(true);

        ruleService.delete(ruleId);

        verify(ruleRepo).deleteById(ruleId);
    }

    // AlertEventService edge cases

    @Test
    void listOpenReturnsEmptyList() {
        when(eventRepo.findAllOpen()).thenReturn(List.of());

        var result = eventService.listOpen();

        assertThat(result).isEmpty();
    }

    @Test
    void listOpenReturnsOnlyOpenEvents() {
        AlertEvent open1 = new AlertEvent(UUID.randomUUID(), UUID.randomUUID(), "cpu_high");
        AlertEvent open2 = new AlertEvent(UUID.randomUUID(), UUID.randomUUID(), "memory_high");

        when(eventRepo.findAllOpen()).thenReturn(List.of(open1, open2));

        var result = eventService.listOpen();

        assertThat(result).hasSize(2);
    }

    @Test
    void listAllReturnsAllEvents() {
        AlertEvent event1 = new AlertEvent(UUID.randomUUID(), UUID.randomUUID(), "cpu_high");
        AlertEvent event2 = new AlertEvent(UUID.randomUUID(), UUID.randomUUID(), "memory_high");

        when(eventRepo.findAllOrderByTriggeredAtDesc()).thenReturn(List.of(event1, event2));

        var result = eventService.listAll();

        assertThat(result).hasSize(2);
    }

    @Test
    void ackNonExistentEventDoesNothing() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(eventRepo.findById(eventId)).thenReturn(Optional.empty());

        eventService.ack(eventId, userId);

        verify(eventRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void ackOpenEventPublishesNotification() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AlertEvent event = new AlertEvent(eventId, UUID.randomUUID(), "cpu_high");

        when(eventRepo.findById(eventId)).thenReturn(Optional.of(event));

        eventService.ack(eventId, userId);

        verify(eventRepo).save(event);
        verify(eventPublisher).publish(argThat(ev ->
            "alert.acknowledged".equals(ev.getType())
        ));
    }

    @Test
    void ackAlreadyClosedEventDoesNotPublish() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AlertEvent event = new AlertEvent(eventId, UUID.randomUUID(), "cpu_high");
        event.ack(UUID.randomUUID());

        when(eventRepo.findById(eventId)).thenReturn(Optional.of(event));

        eventService.ack(eventId, userId);

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void ackEventSetsAckedBy() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AlertEvent event = new AlertEvent(eventId, UUID.randomUUID(), "memory_high");

        when(eventRepo.findById(eventId)).thenReturn(Optional.of(event));

        eventService.ack(eventId, userId);

        verify(eventRepo).save(argThat(ev ->
            ev.getAckedBy() != null && ev.getAckedBy().equals(userId)
        ));
    }

    @Test
    void ackEventSetsAckedAtTimestamp() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AlertEvent event = new AlertEvent(eventId, UUID.randomUUID(), "disk_high");
        OffsetDateTime before = OffsetDateTime.now();

        when(eventRepo.findById(eventId)).thenReturn(Optional.of(event));

        eventService.ack(eventId, userId);

        verify(eventRepo).save(argThat(ev -> {
            OffsetDateTime ackedAt = ev.getAckedAt();
            return ackedAt != null && ackedAt.isAfter(before);
        }));
    }

    @Test
    void listAllReturnsEmptyList() {
        when(eventRepo.findAllOrderByTriggeredAtDesc()).thenReturn(List.of());

        var result = eventService.listAll();

        assertThat(result).isEmpty();
    }
}
