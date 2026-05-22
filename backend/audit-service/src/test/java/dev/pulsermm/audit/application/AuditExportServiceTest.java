package dev.pulsermm.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.audit.domain.AuditEvent;
import dev.pulsermm.audit.infrastructure.persistence.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditExportServiceTest {

    private AuditEventRepository repository;
    private ObjectMapper objectMapper;
    private AuditExportService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditEventRepository.class);
        objectMapper = new ObjectMapper();
        service = new AuditExportService(repository, objectMapper);
    }

    @Test
    void streamCsvWritesHeader() throws IOException {
        Page<AuditEvent> page = new PageImpl<>(List.of(), PageRequest.of(0, 500), 0);
        when(repository.findFiltered(any(), any(), any(), any(), any())).thenReturn(page);

        var out = new ByteArrayOutputStream();
        service.streamCsv(null, null, null, null, out);
        String csv = out.toString();

        assertThat(csv).startsWith("id,user_id,username,permission_used,action,endpoint_id,payload,created_at");
    }

    @Test
    void streamCsvWithSingleEvent() throws IOException {
        UUID userId = UUID.randomUUID();
        var event = new AuditEvent(UUID.randomUUID(), userId, "alice", "auth:login", "user.login", null, "{}", Instant.parse("2026-05-18T12:00:00Z"));
        Page<AuditEvent> page = new PageImpl<>(List.of(event), PageRequest.of(0, 500), 1);
        when(repository.findFiltered(any(), any(), any(), any(), any())).thenReturn(page);

        var out = new ByteArrayOutputStream();
        service.streamCsv(null, null, null, null, out);
        String csv = out.toString();

        assertThat(csv).contains("alice");
        assertThat(csv).contains("auth:login");
    }

    @Test
    void streamCsvEscapesCommas() throws IOException {
        var event = new AuditEvent(UUID.randomUUID(), UUID.randomUUID(), "alice,bob", "auth:login", "user.login", null, "{}", Instant.now());
        Page<AuditEvent> page = new PageImpl<>(List.of(event), PageRequest.of(0, 500), 1);
        when(repository.findFiltered(any(), any(), any(), any(), any())).thenReturn(page);

        var out = new ByteArrayOutputStream();
        service.streamCsv(null, null, null, null, out);
        String csv = out.toString();

        assertThat(csv).contains("\"alice,bob\"");
    }

    @Test
    void streamCsvEscapesQuotes() throws IOException {
        var event = new AuditEvent(UUID.randomUUID(), UUID.randomUUID(), "alice\"bob", "auth:login", "user.login", null, "{}", Instant.now());
        Page<AuditEvent> page = new PageImpl<>(List.of(event), PageRequest.of(0, 500), 1);
        when(repository.findFiltered(any(), any(), any(), any(), any())).thenReturn(page);

        var out = new ByteArrayOutputStream();
        service.streamCsv(null, null, null, null, out);
        String csv = out.toString();

        assertThat(csv).contains("\"alice\"\"bob\"");
    }


    @Test
    void streamCsvHandlesNullValues() throws IOException {
        var event = new AuditEvent(UUID.randomUUID(), null, null, "policy:read", "policy.view", null, null, Instant.now());
        Page<AuditEvent> page = new PageImpl<>(List.of(event), PageRequest.of(0, 500), 1);
        when(repository.findFiltered(any(), any(), any(), any(), any())).thenReturn(page);

        var out = new ByteArrayOutputStream();
        service.streamCsv(null, null, null, null, out);
        String csv = out.toString();

        assertThat(csv).contains("policy:read");
        assertThat(csv).doesNotContain("null");
    }

    @Test
    void streamCsvPayloadWithJsonWorks() throws IOException {
        var payload = "{\"user_id\":\"123\",\"action\":\"login\"}";
        var event = new AuditEvent(UUID.randomUUID(), UUID.randomUUID(), "alice", "auth:login", "user.login", null, payload, Instant.now());
        Page<AuditEvent> page = new PageImpl<>(List.of(event), PageRequest.of(0, 500), 1);
        when(repository.findFiltered(any(), any(), any(), any(), any())).thenReturn(page);

        var out = new ByteArrayOutputStream();
        service.streamCsv(null, null, null, null, out);
        String csv = out.toString();

        assertThat(csv).contains("user_id");
    }
}
