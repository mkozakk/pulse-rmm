package dev.pulsermm.metric.application;

import dev.pulsermm.metric.infrastructure.EndpointHeartbeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class OfflineMarkerJob {

    private static final Logger logger = LoggerFactory.getLogger(OfflineMarkerJob.class);

    private final EndpointHeartbeatRepository heartbeatRepo;

    public OfflineMarkerJob(EndpointHeartbeatRepository heartbeatRepo) {
        this.heartbeatRepo = heartbeatRepo;
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void markStaleEndpointsOffline() {
        Instant cutoff = Instant.now().minusSeconds(90);
        heartbeatRepo.markOfflineWhere(cutoff);
        logger.debug("Marked endpoints offline with last_seen before {}", cutoff);
    }
}
