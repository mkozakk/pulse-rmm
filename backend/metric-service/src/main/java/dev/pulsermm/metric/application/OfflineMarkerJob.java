package dev.pulsermm.metric.application;

import dev.pulsermm.common.events.DomainEvent;
import dev.pulsermm.common.events.DomainEventPublisher;
import dev.pulsermm.metric.infrastructure.EndpointHeartbeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OfflineMarkerJob {

    private static final Logger logger = LoggerFactory.getLogger(OfflineMarkerJob.class);

    private final EndpointHeartbeatRepository heartbeatRepo;
    private final DomainEventPublisher domainEventPublisher;

    public OfflineMarkerJob(EndpointHeartbeatRepository heartbeatRepo, DomainEventPublisher domainEventPublisher) {
        this.heartbeatRepo = heartbeatRepo;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void markStaleEndpointsOffline() {
        Instant cutoff = Instant.now().minusSeconds(90);
        List<UUID> goingOffline = heartbeatRepo.findOnlineWithLastSeenBefore(cutoff);
        heartbeatRepo.markOfflineWhere(cutoff);
        logger.debug("Marked {} endpoints offline with last_seen before {}", goingOffline.size(), cutoff);
        for (UUID endpointId : goingOffline) {
            domainEventPublisher.publish(DomainEvent.of("endpoint.offline",
                Map.of("endpointId", endpointId.toString())));
        }
    }
}
