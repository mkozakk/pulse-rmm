package dev.pulsermm.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.audit.domain.AuditEvent;
import dev.pulsermm.audit.infrastructure.persistence.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditServiceEdgeCasesTest {

    private AuditEventRepository auditRepo;
    private ObjectMapper objectMapper;
    private AuditQueryService queryService;
    private AuditExportService exportService;

    @BeforeEach
    void setUp() {
        auditRepo = mock(AuditEventRepository.class);
        objectMapper = new ObjectMapper();

        queryService = new AuditQueryService(auditRepo);
        exportService = new AuditExportService(auditRepo, objectMapper);
    }

    // AuditQueryService edge cases

    @Test
    void listWithAllNullFiltersReturnsEmpty() {
        PageRequest pageReq = PageRequest.of(0, 10);
        when(auditRepo.findFiltered(null, null, null, null, pageReq))
            .thenReturn(new PageImpl<>(List.of(), pageReq, 0));

        var result = queryService.list(null, null, null, null, pageReq);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void listWithUserIdFilter() {
        UUID userId = UUID.randomUUID();
        PageRequest pageReq = PageRequest.of(0, 10);

        when(auditRepo.findFiltered(userId, null, null, null, pageReq))
            .thenReturn(new PageImpl<>(List.of(), pageReq, 0));

        var result = queryService.list(userId, null, null, null, pageReq);

        verify(auditRepo).findFiltered(userId, null, null, null, pageReq);
    }

    @Test
    void listWithEndpointIdFilter() {
        UUID endpointId = UUID.randomUUID();
        PageRequest pageReq = PageRequest.of(0, 10);

        when(auditRepo.findFiltered(null, endpointId, null, null, pageReq))
            .thenReturn(new PageImpl<>(List.of(), pageReq, 0));

        var result = queryService.list(null, endpointId, null, null, pageReq);

        verify(auditRepo).findFiltered(null, endpointId, null, null, pageReq);
    }

    @Test
    void listWithTimeRangeFilter() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        PageRequest pageReq = PageRequest.of(0, 10);

        when(auditRepo.findFiltered(null, null, from, to, pageReq))
            .thenReturn(new PageImpl<>(List.of(), pageReq, 0));

        var result = queryService.list(null, null, from, to, pageReq);

        verify(auditRepo).findFiltered(null, null, from, to, pageReq);
    }

    @Test
    void listWithSameFromAndToTimestamp() {
        Instant timestamp = Instant.now();
        PageRequest pageReq = PageRequest.of(0, 10);

        when(auditRepo.findFiltered(null, null, timestamp, timestamp, pageReq))
            .thenReturn(new PageImpl<>(List.of(), pageReq, 0));

        var result = queryService.list(null, null, timestamp, timestamp, pageReq);

        assertThat(result.getContent()).isEmpty();
    }

    // AuditExportService edge cases

    @Test
    void streamCsvWithNoEventsWritesOnlyHeader() throws IOException {
        PageRequest pageReq = PageRequest.of(0, 500);
        when(auditRepo.findFiltered(null, null, null, null, pageReq))
            .thenReturn(new PageImpl<>(List.of(), pageReq, 0));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.streamCsv(null, null, null, null, out);

        String result = out.toString();
        assertThat(result).contains("id,user_id,username");
        assertThat(result.lines().count()).isEqualTo(1);
    }

    @Test
    void streamCsvWithEventsIncludesData() throws IOException {
        PageRequest pageReq = PageRequest.of(0, 500);
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());
        event.setUsername("testuser");
        event.setAction("user.login");
        event.setPermissionUsed("auth:login");

        when(auditRepo.findFiltered(null, null, null, null, pageReq))
            .thenReturn(new PageImpl<>(List.of(event), pageReq, 1));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.streamCsv(null, null, null, null, out);

        String result = out.toString();
        assertThat(result).contains("testuser");
        assertThat(result).contains("user.login");
    }

    @Test
    void streamCsvEscapesCommasInValues() throws IOException {
        PageRequest pageReq = PageRequest.of(0, 500);
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());
        event.setUsername("user,with,commas");
        event.setAction("test");

        when(auditRepo.findFiltered(null, null, null, null, pageReq))
            .thenReturn(new PageImpl<>(List.of(event), pageReq, 1));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.streamCsv(null, null, null, null, out);

        String result = out.toString();
        assertThat(result).contains("\"user,with,commas\"");
    }

    @Test
    void streamCsvEscapesQuotesInValues() throws IOException {
        PageRequest pageReq = PageRequest.of(0, 500);
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());
        event.setUsername("user\"with\"quotes");
        event.setAction("test");

        when(auditRepo.findFiltered(null, null, null, null, pageReq))
            .thenReturn(new PageImpl<>(List.of(event), pageReq, 1));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.streamCsv(null, null, null, null, out);

        String result = out.toString();
        assertThat(result).contains("user\"\"with\"\"quotes");
    }

    @Test
    void streamCsvEscapesNewlinesInValues() throws IOException {
        PageRequest pageReq = PageRequest.of(0, 500);
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());
        event.setPayload("line1\nline2\nline3");
        event.setAction("test");

        when(auditRepo.findFiltered(null, null, null, null, pageReq))
            .thenReturn(new PageImpl<>(List.of(event), pageReq, 1));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.streamCsv(null, null, null, null, out);

        String result = out.toString();
        assertThat(result).contains("\"line1");
    }

    @Test
    void streamCsvHandlesNullValues() throws IOException {
        PageRequest pageReq = PageRequest.of(0, 500);
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());
        event.setUsername("testuser");
        event.setEndpointId(null);
        event.setPayload(null);
        event.setAction("test");

        when(auditRepo.findFiltered(null, null, null, null, pageReq))
            .thenReturn(new PageImpl<>(List.of(event), pageReq, 1));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.streamCsv(null, null, null, null, out);

        String result = out.toString();
        assertThat(result).contains("testuser,,");
    }

    @Test
    void streamNdjsonWithNoEventsWritesNothing() throws IOException {
        PageRequest pageReq = PageRequest.of(0, 500);
        when(auditRepo.findFiltered(null, null, null, null, pageReq))
            .thenReturn(new PageImpl<>(List.of(), pageReq, 0));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.streamNdjson(null, null, null, null, out);

        String result = out.toString();
        assertThat(result).isEmpty();
    }

    @Test
    void streamNdjsonWithEventsWritesJsonLines() throws IOException {
        PageRequest pageReq = PageRequest.of(0, 500);
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());
        event.setUsername("testuser");
        event.setAction("test.action");

        when(auditRepo.findFiltered(null, null, null, null, pageReq))
            .thenReturn(new PageImpl<>(List.of(event), pageReq, 1));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.streamNdjson(null, null, null, null, out);

        String result = out.toString();
        assertThat(result).contains("\"username\":\"testuser\"");
        assertThat(result).contains("\"action\":\"test.action\"");
    }

    @Test
    void streamNdjsonWithMultipleEventsSeparatedByNewlines() throws IOException {
        PageRequest pageReq = PageRequest.of(0, 500);
        AuditEvent event1 = new AuditEvent();
        event1.setId(UUID.randomUUID());
        event1.setUsername("user1");
        event1.setAction("action1");

        AuditEvent event2 = new AuditEvent();
        event2.setId(UUID.randomUUID());
        event2.setUsername("user2");
        event2.setAction("action2");

        when(auditRepo.findFiltered(null, null, null, null, pageReq))
            .thenReturn(new PageImpl<>(List.of(event1, event2), pageReq, 2));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.streamNdjson(null, null, null, null, out);

        String result = out.toString();
        String[] lines = result.split("\n");
        assertThat(lines).hasLength(2);
    }

    @Test
    void streamCsvWithMultiplePagesIteratesAll() throws IOException {
        PageRequest pageReq0 = PageRequest.of(0, 500);
        PageRequest pageReq1 = PageRequest.of(1, 500);

        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setUsername("testuser");
        event.setAction("test");

        when(auditRepo.findFiltered(null, null, null, null, pageReq0))
            .thenReturn(new PageImpl<>(List.of(event), pageReq0, 600) {
                @Override
                public boolean hasNext() {
                    return true;
                }
            });

        when(auditRepo.findFiltered(null, null, null, null, pageReq1))
            .thenReturn(new PageImpl<>(List.of(event), pageReq1, 600) {
                @Override
                public boolean hasNext() {
                    return false;
                }
            });

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.streamCsv(null, null, null, null, out);

        verify(auditRepo, times(2)).findFiltered(null, null, null, null, any());
    }
}
