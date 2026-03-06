package dev.pulsermm.enrolment.application;

import dev.pulsermm.enrolment.api.errors.InvalidTokenException;
import dev.pulsermm.enrolment.domain.Endpoint;
import dev.pulsermm.enrolment.domain.EnrolmentToken;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import dev.pulsermm.enrolment.infrastructure.EnrolmentTokenRepository;
import dev.pulsermm.common.audit.Auditable;
import dev.pulsermm.common.events.DomainEvent;
import dev.pulsermm.common.events.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class EnrolService {
    private final EnrolmentTokenRepository tokenRepository;
    private final EndpointRepository endpointRepository;
    private final TagRuleService tagRuleService;
    private final DomainEventPublisher domainEventPublisher;

    public EnrolService(EnrolmentTokenRepository tokenRepository,
                        EndpointRepository endpointRepository,
                        TagRuleService tagRuleService,
                        DomainEventPublisher domainEventPublisher) {
        this.tokenRepository = tokenRepository;
        this.endpointRepository = endpointRepository;
        this.tagRuleService = tagRuleService;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Auditable(action = "endpoint.enrol", permission = "enrolment:manage")
    public UUID enrol(UUID tokenId, byte[] publicKey, String hostname, String os, String arch) {
        Instant now = Instant.now();

        EnrolmentToken token = tokenRepository.findByIdAndRevokedFalseAndExpiresAtAfter(tokenId, now)
            .orElseThrow(() -> new InvalidTokenException("Token not found or expired"));

        var existing = endpointRepository.findByPublicKey(publicKey);
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        Endpoint endpoint = new Endpoint(
            UUID.randomUUID(),
            hostname,
            os,
            arch,
            token.getGroupId(),
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
        return saved.getId();
    }
}
