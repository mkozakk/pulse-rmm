package dev.pulsermm.audit.application;

import dev.pulsermm.audit.domain.AuditEvent;
import dev.pulsermm.audit.infrastructure.persistence.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class AuditQueryServiceTest {

    private AuditEventRepository repository;
    private AuditQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditEventRepository.class);
        service = new AuditQueryService(repository);
    }

    @Test
    void listCallsRepositoryWithFilters() {
        UUID userId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        Pageable pageable = PageRequest.of(0, 20);
        var event = new AuditEvent(UUID.randomUUID(), userId, "alice", "auth:login", "user.login", endpointId, "{}", Instant.now());
        Page<AuditEvent> page = new PageImpl<>(List.of(event), pageable, 1);

        when(repository.findFiltered(userId, endpointId, from, to, pageable)).thenReturn(page);

        var result = service.list(userId, endpointId, from, to, pageable);

        assertThat(result).hasSize(1);
        verify(repository).findFiltered(userId, endpointId, from, to, pageable);
    }

    @Test
    void listWithNullFiltersAllowed() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AuditEvent> page = new PageImpl<>(List.of(), pageable, 0);

        when(repository.findFiltered(null, null, null, null, pageable)).thenReturn(page);

        var result = service.list(null, null, null, null, pageable);

        assertThat(result).isEmpty();
    }

    @Test
    void listWithPaginationWorks() {
        var event1 = new AuditEvent(UUID.randomUUID(), UUID.randomUUID(), "alice", "auth:login", "user.login", null, "{}", Instant.now());
        var event2 = new AuditEvent(UUID.randomUUID(), UUID.randomUUID(), "bob", "auth:logout", "user.logout", null, "{}", Instant.now());
        Pageable pageable = PageRequest.of(0, 2);
        Page<AuditEvent> page = new PageImpl<>(List.of(event1, event2), pageable, 100);

        when(repository.findFiltered(null, null, null, null, pageable)).thenReturn(page);

        var result = service.list(null, null, null, null, pageable);

        assertThat(result).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(100);
    }

    @Test
    void listEmptyResultWorks() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AuditEvent> page = new PageImpl<>(List.of(), pageable, 0);

        when(repository.findFiltered(null, null, null, null, pageable)).thenReturn(page);

        var result = service.list(null, null, null, null, pageable);

        assertThat(result).isEmpty();
    }
}
