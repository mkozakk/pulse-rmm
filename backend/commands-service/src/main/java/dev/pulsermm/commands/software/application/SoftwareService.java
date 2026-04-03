package dev.pulsermm.commands.software.application;

import dev.pulsermm.common.audit.Auditable;
import dev.pulsermm.common.audit.EndpointId;
import dev.pulsermm.common.events.DomainEvent;
import dev.pulsermm.common.events.DomainEventPublisher;
import dev.pulsermm.commands.software.domain.SoftwareCommand;
import dev.pulsermm.commands.software.domain.SoftwareItem;
import dev.pulsermm.commands.infrastructure.GatewayClient;
import dev.pulsermm.commands.software.infrastructure.SoftwareCommandRepository;
import dev.pulsermm.commands.software.infrastructure.SoftwareItemRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SoftwareService {
    private final SoftwareItemRepository softwareItemRepository;
    private final SoftwareCommandRepository softwareCommandRepository;
    private final GatewayClient gatewayClient;
    private final EntityManager entityManager;
    private final DomainEventPublisher domainEventPublisher;

    public SoftwareService(SoftwareItemRepository softwareItemRepository, SoftwareCommandRepository softwareCommandRepository, GatewayClient gatewayClient, EntityManager entityManager, DomainEventPublisher domainEventPublisher) {
        this.softwareItemRepository = softwareItemRepository;
        this.softwareCommandRepository = softwareCommandRepository;
        this.gatewayClient = gatewayClient;
        this.entityManager = entityManager;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public void storeSoftwareList(UUID endpointId, List<SoftwareItemDTO> items) {
        var logger = org.slf4j.LoggerFactory.getLogger(SoftwareService.class);
        logger.info("storeSoftwareList called: endpoint={}, items={}", endpointId, items.size());

        softwareItemRepository.deleteByEndpointId(endpointId);
        entityManager.flush();
        logger.info("Deleted and flushed existing items for endpoint={}", endpointId);

        var softwareItems = items.stream()
                .map(item -> {
                    String finalAppId = (item.appId() != null && !item.appId().isBlank()) ? item.appId() : item.name();
                    return new SoftwareItem(
                            UUID.randomUUID(),
                            endpointId,
                            item.name(),
                            finalAppId,
                            item.version(),
                            item.updateTo(),
                            item.isStore(),
                            item.source(),
                            LocalDateTime.now()
                    );
                })
                .toList();

        logger.info("Saving {} items for endpoint={}", softwareItems.size(), endpointId);
        softwareItemRepository.saveAll(softwareItems);
        entityManager.flush();
        logger.info("Successfully saved {} items for endpoint={}", softwareItems.size(), endpointId);
    }

    @Transactional(readOnly = true)
    public List<SoftwareItem> getSoftwareList(UUID endpointId) {
        return softwareItemRepository.findByEndpointId(endpointId);
    }

    @Auditable(action = "software.command", permission = "software:manage")
    @Transactional
    public SoftwareCommand createCommand(@EndpointId UUID endpointId, String action, String packageName, String appId, String packageVersion) {
        var cmdId = UUID.randomUUID();
        var cmd = new SoftwareCommand(cmdId, endpointId, action, packageName, appId, packageVersion);
        softwareCommandRepository.save(cmd);
        org.slf4j.LoggerFactory.getLogger(SoftwareService.class).info(
            "Created software command: id={}, endpoint={}, action={}, package={}, appId={}",
            cmdId, endpointId, action, packageName, appId);
        gatewayClient.dispatchSoftwareCommand(endpointId, cmdId.toString(), action, packageName, appId, packageVersion);
        return cmd;
    }

    @Transactional
    public void ackCommand(UUID commandId, int exitCode, String output) {
        var cmd = softwareCommandRepository.findById(commandId).orElseThrow();
        cmd.complete(exitCode, output);
        softwareCommandRepository.save(cmd);

        domainEventPublisher.publish(DomainEvent.of("software.command.completed", Map.of(
                "commandId", commandId.toString(),
                "endpointId", cmd.endpointId().toString(),
                "action", cmd.action(),
                "exitCode", exitCode
        )));
    }

    @Transactional(readOnly = true)
    public SoftwareCommand getCommand(UUID commandId) {
        return softwareCommandRepository.findById(commandId).orElseThrow();
    }

    public record SoftwareItemDTO(String name, String appId, String version, String updateTo, Boolean isStore, String source) {}
}
