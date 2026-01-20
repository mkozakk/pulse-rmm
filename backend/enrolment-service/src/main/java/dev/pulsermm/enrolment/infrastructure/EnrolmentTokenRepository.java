package dev.pulsermm.enrolment.infrastructure;

import dev.pulsermm.enrolment.domain.EnrolmentToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrolmentTokenRepository extends JpaRepository<EnrolmentToken, UUID> {
    Optional<EnrolmentToken> findByIdAndRevokedFalseAndExpiresAtAfter(UUID id, Instant now);
}
