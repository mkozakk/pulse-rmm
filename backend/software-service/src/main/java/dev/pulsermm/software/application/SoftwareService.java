package dev.pulsermm.software.application;

import dev.pulsermm.software.domain.SoftwareCommand;
import dev.pulsermm.software.domain.SoftwareItem;
import dev.pulsermm.software.infrastructure.GatewayClient;
import dev.pulsermm.software.infrastructure.SoftwareCommandRepository;
import dev.pulsermm.software.infrastructure.SoftwareItemRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SoftwareService {
    private final SoftwareItemRepository softwareItemRepository;
    private final SoftwareCommandRepository softwareCommandRepository;
    private final GatewayClient gatewayClient;
    private final EntityManager entityManager;

    public SoftwareService(SoftwareItemRepository softwareItemRepository, SoftwareCommandRepository softwareCommandRepository, GatewayClient gatewayClient, EntityManager entityManager) {
        this.softwareItemRepository = softwareItemRepository;
        this.softwareCommandRepository = softwareCommandRepository;
        this.gatewayClient = gatewayClient;
        this.entityManager = entityManager;
    }

    @Transactional
    public void storeSoftwareList(UUID endpointId, List<SoftwareItemDTO> items) {
        var logger = org.slf4j.LoggerFactory.getLogger(SoftwareService.class);
        logger.info("storeSoftwareList called: endpoint={}, items={}", endpointId, items.size());

        softwareItemRepository.deleteByEndpointId(endpointId);
        entityManager.flush();
        logger.info("Deleted and flushed existing items for endpoint={}", endpointId);

        var softwareItems = items.stream()
                .map(item -> new SoftwareItem(
                        UUID.randomUUID(),
                        endpointId,
                        item.name(),
                        item.appId(),
                        item.version(),
                        item.updateTo(),
                        item.isStore(),
                        item.source(),
                        LocalDateTime.now()
                ))
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

    @Transactional
    public SoftwareCommand createCommand(UUID endpointId, String action, String packageName, String appId, String packageVersion) {
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
    }

    @Transactional(readOnly = true)
    public SoftwareCommand getCommand(UUID commandId) {
        return softwareCommandRepository.findById(commandId).orElseThrow();
    }

    public record SoftwareItemDTO(String name, String appId, String version, String updateTo, Boolean isStore, String source) {}
}
