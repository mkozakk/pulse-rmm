package dev.pulsermm.enrolment.infrastructure;

import dev.pulsermm.enrolment.domain.EnrolmentToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrolmentTokenRepository extends JpaRepository<EnrolmentToken, UUID> {
    Optional<EnrolmentToken> findByIdAndRevokedFalseAndExpiresAtAfter(UUID id, Instant now);

    @Query(value = """
        SELECT * FROM enrolment.enrolment_tokens
         WHERE id = :id
           AND revoked = FALSE
           AND expires_at > :now
           AND consumed_at IS NULL
        """, nativeQuery = true)
    Optional<EnrolmentToken> findLive(@Param("id") UUID id, @Param("now") Instant now);
}
