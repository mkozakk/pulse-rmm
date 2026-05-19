package dev.pulsermm.enrolment.application;

import dev.pulsermm.enrolment.api.errors.InvalidTokenException;
import dev.pulsermm.enrolment.domain.Endpoint;
import dev.pulsermm.enrolment.infrastructure.CaClient;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import dev.pulsermm.common.audit.Auditable;
import dev.pulsermm.common.events.DomainEvent;
import dev.pulsermm.common.events.DomainEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class EnrolService {
    private final EndpointRepository endpointRepository;
    private final TagRuleService tagRuleService;
    private final DomainEventPublisher domainEventPublisher;
    private final JdbcTemplate jdbc;
    private final CaClient caClient;

    public EnrolService(EndpointRepository endpointRepository,
                        TagRuleService tagRuleService,
                        DomainEventPublisher domainEventPublisher,
                        JdbcTemplate jdbc,
                        @Autowired(required = false) CaClient caClient) {
        this.endpointRepository = endpointRepository;
        this.tagRuleService = tagRuleService;
        this.domainEventPublisher = domainEventPublisher;
        this.jdbc = jdbc;
        this.caClient = caClient;
    }

    public record EnrolResult(UUID endpointId, String certPem, String caBundlePem) {}

    @Auditable(action = "endpoint.enrol", permission = "enrolment:manage")
    public UUID enrol(UUID tokenId, byte[] publicKey, String hostname, String os, String arch) {
        return enrolWithCsr(tokenId, publicKey, hostname, os, arch, null).endpointId();
    }

    @Auditable(action = "endpoint.enrol", permission = "enrolment:manage")
    public EnrolResult enrolWithCsr(UUID tokenId, byte[] publicKey, String hostname, String os, String arch, String csrPem) {
        Instant now = Instant.now();

        var existing = endpointRepository.findByPublicKey(publicKey);
        if (existing.isPresent()) {
            return new EnrolResult(existing.get().getId(), null, null);
        }

        UUID endpointId = UUID.randomUUID();

        UUID groupId = jdbc.query(
            """
            UPDATE enrolment.enrolment_tokens
               SET consumed_at = ?, consumed_by_endpoint = ?
             WHERE id = ?
               AND consumed_at IS NULL
               AND revoked = FALSE
               AND expires_at > ?
            RETURNING group_id
            """,
            rs -> rs.next() ? (UUID) rs.getObject("group_id") : null,
            Timestamp.from(now), endpointId, tokenId, Timestamp.from(now));

        if (groupId == null) {
            throw new InvalidTokenException("Token not found, expired, revoked, or already consumed");
        }

        Endpoint endpoint = new Endpoint(
            endpointId,
            hostname,
            os,
            arch,
            groupId,
            publicKey,
            now,
            now
        );

        Endpoint saved = endpointRepository.save(endpoint);
        tagRuleService.applyRulesTo(saved);
        domainEventPublisher.publish(DomainEvent.of("endpoint.enrolled", Map.of(
            "endpointId", saved.getId().toString(),
            "hostname", saved.getHostname(),
            "os", saved.getOs(),
            "groupId", saved.getGroupId().toString()
        )));

        String certPem = null;
        String caBundlePem = null;
        if (caClient != null && csrPem != null && !csrPem.isBlank()) {
            var signed = caClient.sign(csrPem, saved.getId());
            certPem = signed.certPem();
            caBundlePem = signed.caBundlePem();
        }
        return new EnrolResult(saved.getId(), certPem, caBundlePem);
    }
}
