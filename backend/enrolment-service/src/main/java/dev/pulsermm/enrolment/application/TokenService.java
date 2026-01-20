package dev.pulsermm.enrolment.application;

import dev.pulsermm.enrolment.domain.EnrolmentToken;
import dev.pulsermm.enrolment.domain.Group;
import dev.pulsermm.enrolment.infrastructure.EnrolmentTokenRepository;
import dev.pulsermm.enrolment.infrastructure.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class TokenService {
    private final EnrolmentTokenRepository tokenRepository;
    private final GroupRepository groupRepository;

    public TokenService(EnrolmentTokenRepository tokenRepository, GroupRepository groupRepository) {
        this.tokenRepository = tokenRepository;
        this.groupRepository = groupRepository;
    }

    public EnrolmentToken createToken(UUID groupId, int ttlHours) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds((long) ttlHours * 3600);

        EnrolmentToken token = new EnrolmentToken(
            UUID.randomUUID(),
            groupId,
            expiresAt,
            false,
            now
        );

        return tokenRepository.save(token);
    }
}
