package dev.pulsermm.remote.infrastructure.persistence;

import dev.pulsermm.remote.domain.DesktopSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<DesktopSession, UUID> {
    @Query("SELECT s FROM DesktopSession s WHERE s.status = 'pending' AND s.createdAt < :cutoff")
    List<DesktopSession> findStalePending(OffsetDateTime cutoff);
}
