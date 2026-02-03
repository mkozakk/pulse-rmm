package dev.pulsermm.enrolment.application;

import dev.pulsermm.enrolment.api.errors.InvalidTokenException;
import dev.pulsermm.enrolment.domain.Endpoint;
import dev.pulsermm.enrolment.domain.EnrolmentToken;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import dev.pulsermm.enrolment.infrastructure.EnrolmentTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class EnrolService {
    private final EnrolmentTokenRepository tokenRepository;
    private final EndpointRepository endpointRepository;
    private final TagRuleService tagRuleService;

    public EnrolService(EnrolmentTokenRepository tokenRepository,
                        EndpointRepository endpointRepository,
                        TagRuleService tagRuleService) {
        this.tokenRepository = tokenRepository;
        this.endpointRepository = endpointRepository;
        this.tagRuleService = tagRuleService;
    }

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
        return saved.getId();
    }
}
