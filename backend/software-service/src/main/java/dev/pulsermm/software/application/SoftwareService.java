package dev.pulsermm.software.application;

import dev.pulsermm.software.domain.SoftwareCommand;
import dev.pulsermm.software.domain.SoftwareItem;
import dev.pulsermm.software.infrastructure.GatewayClient;
import dev.pulsermm.software.infrastructure.SoftwareCommandRepository;
import dev.pulsermm.software.infrastructure.SoftwareItemRepository;
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

    public SoftwareService(SoftwareItemRepository softwareItemRepository, SoftwareCommandRepository softwareCommandRepository, GatewayClient gatewayClient) {
        this.softwareItemRepository = softwareItemRepository;
        this.softwareCommandRepository = softwareCommandRepository;
        this.gatewayClient = gatewayClient;
    }

    @Transactional
    public void storeSoftwareList(UUID endpointId, List<SoftwareItemDTO> items) {
        softwareItemRepository.deleteAll(softwareItemRepository.findByEndpointId(endpointId));
        items.forEach(item -> {
            var softwareItem = new SoftwareItem(
                UUID.randomUUID(),
                endpointId,
                item.name(),
                item.version(),
                item.source(),
                LocalDateTime.now()
            );
            softwareItemRepository.save(softwareItem);
        });
    }

    @Transactional(readOnly = true)
    public List<SoftwareItem> getSoftwareList(UUID endpointId) {
        return softwareItemRepository.findByEndpointId(endpointId);
    }

    @Transactional
    public SoftwareCommand createCommand(UUID endpointId, String action, String packageName, String packageVersion) {
        var cmdId = UUID.randomUUID();
        var cmd = new SoftwareCommand(cmdId, endpointId, action, packageName, packageVersion);
        softwareCommandRepository.save(cmd);
        gatewayClient.dispatchSoftwareCommand(endpointId, cmdId.toString(), action, packageName, packageVersion);
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

    public record SoftwareItemDTO(String name, String version, String source) {}
}
