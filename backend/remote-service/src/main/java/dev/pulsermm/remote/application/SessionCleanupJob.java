package dev.pulsermm.remote.application;

import dev.pulsermm.remote.infrastructure.persistence.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
public class SessionCleanupJob {

    private static final Logger logger = LoggerFactory.getLogger(SessionCleanupJob.class);

    private final SessionRepository sessionRepository;

    public SessionCleanupJob(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cleanupStaleSessions() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(5);
        var stale = sessionRepository.findStalePending(cutoff);
        for (var session : stale) {
            session.markEnded();
            sessionRepository.save(session);
            logger.info("Cleaned up stale pending session {} (endpoint={}, created={})",
                session.getId(), session.getEndpointId(), session.getCreatedAt());
        }
        if (!stale.isEmpty()) {
            logger.info("Cleaned up {} stale pending desktop sessions", stale.size());
        }
    }
}
