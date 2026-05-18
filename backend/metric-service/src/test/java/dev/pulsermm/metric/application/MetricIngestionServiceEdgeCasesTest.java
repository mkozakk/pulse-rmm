package dev.pulsermm.metric.application;

import dev.pulsermm.common.events.DomainEventPublisher;
import dev.pulsermm.metric.domain.EndpointHeartbeat;
import dev.pulsermm.metric.infrastructure.EndpointHeartbeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetricIngestionServiceEdgeCasesTest {

    private EndpointHeartbeatRepository heartbeatRepo;
    private JdbcTemplate jdbc;
    private DomainEventPublisher eventPublisher;
    private MetricIngestionService service;

    @BeforeEach
    void setUp() {
        heartbeatRepo = mock(EndpointHeartbeatRepository.class);
        jdbc = mock(JdbcTemplate.class);
        eventPublisher = mock(DomainEventPublisher.class);

        service = new MetricIngestionService(heartbeatRepo, jdbc, eventPublisher);
    }

    @Test
    void heartbeatForNewEndpointCreatesRecord() {
        UUID endpointId = UUID.randomUUID();
        when(heartbeatRepo.findById(endpointId)).thenReturn(Optional.empty());

        service.heartbeat(endpointId);

        verify(heartbeatRepo).save(argThat(hb ->
            hb.getEndpointId().equals(endpointId) && "online".equals(hb.getStatus())
        ));
    }

    @Test
    void heartbeatForOnlineEndpointDoesNotPublishEvent() {
        UUID endpointId = UUID.randomUUID();
        EndpointHeartbeat hb = new EndpointHeartbeat(endpointId, Instant.now(), "online");

        when(heartbeatRepo.findById(endpointId)).thenReturn(Optional.of(hb));

        service.heartbeat(endpointId);

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void heartbeatForOfflineEndpointPublishesOnlineEvent() {
        UUID endpointId = UUID.randomUUID();
        EndpointHeartbeat hb = new EndpointHeartbeat(endpointId, Instant.now().minusSeconds(3600), "offline");

        when(heartbeatRepo.findById(endpointId)).thenReturn(Optional.of(hb));

        service.heartbeat(endpointId);

        verify(eventPublisher).publish(argThat(event ->
            "endpoint.online".equals(event.getType())
        ));
    }

    @Test
    void heartbeatUpdatesLastSeenTime() {
        UUID endpointId = UUID.randomUUID();
        Instant oldTime = Instant.now().minusSeconds(3600);
        EndpointHeartbeat hb = new EndpointHeartbeat(endpointId, oldTime, "online");

        when(heartbeatRepo.findById(endpointId)).thenReturn(Optional.of(hb));

        service.heartbeat(endpointId);

        assertThat(hb.getLastSeen()).isAfter(oldTime);
    }

    @Test
    void pushMetricsWithEmptyListDoesNothing() {
        UUID endpointId = UUID.randomUUID();

        service.pushMetrics(endpointId, List.of());

        verify(jdbc, never()).update(any(), any());
    }

    @Test
    void pushMetricsWithSingleSampleInsertsCorrectly() {
        UUID endpointId = UUID.randomUUID();
        Instant now = Instant.now();
        var sample = new MetricIngestionService.MetricSampleInput("cpu", 45.5, now);

        service.pushMetrics(endpointId, List.of(sample));

        verify(jdbc).update(contains("INSERT INTO"), eq(endpointId), eq("cpu"), eq(45.5), any());
    }

    @Test
    void pushMetricsWithMultipleSamplesInsertsEach() {
        UUID endpointId = UUID.randomUUID();
        Instant now = Instant.now();
        var samples = List.of(
            new MetricIngestionService.MetricSampleInput("cpu", 40.0, now),
            new MetricIngestionService.MetricSampleInput("memory", 60.0, now),
            new MetricIngestionService.MetricSampleInput("disk", 80.0, now)
        );

        service.pushMetrics(endpointId, samples);

        verify(jdbc, times(3)).update(any(), any());
    }

    @Test
    void pushMetricsWithZeroValueInsertsSuccessfully() {
        UUID endpointId = UUID.randomUUID();
        Instant now = Instant.now();
        var sample = new MetricIngestionService.MetricSampleInput("cpu", 0.0, now);

        service.pushMetrics(endpointId, List.of(sample));

        verify(jdbc).update(any(), eq(endpointId), eq("cpu"), eq(0.0), any());
    }

    @Test
    void pushMetricsWithNegativeValueInsertsSuccessfully() {
        UUID endpointId = UUID.randomUUID();
        Instant now = Instant.now();
        var sample = new MetricIngestionService.MetricSampleInput("load", -5.0, now);

        service.pushMetrics(endpointId, List.of(sample));

        verify(jdbc).update(any(), eq(endpointId), eq("load"), eq(-5.0), any());
    }

    @Test
    void pushMetricsWithVeryLargeValueInsertsSuccessfully() {
        UUID endpointId = UUID.randomUUID();
        Instant now = Instant.now();
        var sample = new MetricIngestionService.MetricSampleInput("bandwidth", 9999999.99, now);

        service.pushMetrics(endpointId, List.of(sample));

        verify(jdbc).update(any(), eq(endpointId), eq("bandwidth"), eq(9999999.99), any());
    }

    @Test
    void queryMetricsWithEmptyResultReturnsEmptyList() {
        UUID endpointId = UUID.randomUUID();
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();

        when(jdbc.queryForList(any(), any())).thenReturn(List.of());

        var result = service.queryMetrics(endpointId, from, to, "cpu");

        assertThat(result).isEmpty();
    }

    @Test
    void queryMetricsWithResultsReturnsList() {
        UUID endpointId = UUID.randomUUID();
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();

        var mockResults = List.of(
            Map.of("sampled_at", from, "value", 40.0),
            Map.of("sampled_at", from.plusSeconds(600), "value", 45.0)
        );
        when(jdbc.queryForList(any(), any())).thenReturn(mockResults);

        var result = service.queryMetrics(endpointId, from, to, "cpu");

        assertThat(result).hasSize(2);
    }

    @Test
    void queryMetricsWithSameFromAndToReturnsResults() {
        UUID endpointId = UUID.randomUUID();
        Instant timestamp = Instant.now();

        when(jdbc.queryForList(any(), any())).thenReturn(List.of());

        service.queryMetrics(endpointId, timestamp, timestamp, "memory");

        verify(jdbc).queryForList(any(), eq(endpointId), eq("memory"), any(), any());
    }

    @Test
    void queryMetricsPassesCorrectParameters() {
        UUID endpointId = UUID.randomUUID();
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        String metricType = "disk";

        when(jdbc.queryForList(any(), any())).thenReturn(List.of());

        service.queryMetrics(endpointId, from, to, metricType);

        verify(jdbc).queryForList(
            argThat(sql -> sql.contains("endpoint_id") && sql.contains("type")),
            eq(endpointId),
            eq(metricType),
            any(),
            any()
        );
    }

    @Test
    void heartbeatStatusTransitionsFromOfflineToOnline() {
        UUID endpointId = UUID.randomUUID();
        EndpointHeartbeat hb = new EndpointHeartbeat(endpointId, Instant.now(), "offline");

        when(heartbeatRepo.findById(endpointId)).thenReturn(Optional.of(hb));

        service.heartbeat(endpointId);

        assertThat(hb.getStatus()).isEqualTo("online");
    }
}
