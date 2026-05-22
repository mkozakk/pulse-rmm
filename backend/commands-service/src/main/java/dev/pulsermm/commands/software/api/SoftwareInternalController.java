package dev.pulsermm.commands.software.api;

import dev.pulsermm.commands.software.application.SoftwareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal")
public class SoftwareInternalController {

    private static final Logger logger = LoggerFactory.getLogger(SoftwareInternalController.class);

    private final SoftwareService softwareService;

    public SoftwareInternalController(SoftwareService softwareService) {
        this.softwareService = softwareService;
    }

    @PostMapping("/commands/{commandId}/ack")
    public ResponseEntity<Void> ackCommand(
            @PathVariable UUID commandId,
            @RequestBody AckRequest request) {
        softwareService.ackCommand(commandId, request.exitCode(), request.output());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/software-list")
    public ResponseEntity<Void> pushSoftwareList(@RequestBody SoftwareListRequest request) {
        logger.info("pushSoftwareList received: endpoint={}, items={}", request.endpointId(), request.items().size());

        // Check for duplicates in request
        var seen = new HashSet<String>();
        var dupes = new java.util.ArrayList<String>();
        for (var item : request.items()) {
            String identifier = (item.id() != null && !item.id().isBlank()) ? item.id() : item.name();
            if (!seen.add(identifier)) {
                dupes.add(identifier);
            }
        }
        if (!dupes.isEmpty()) {
            logger.warn("DUPLICATE PACKAGES IN REQUEST: {}", dupes);
        }

        var items = request.items().stream()
                .map(i -> new SoftwareService.SoftwareItemDTO(i.name(), i.id(), i.version(), i.updateTo(), i.isStore(), i.source()))
                .toList();
        softwareService.storeSoftwareList(request.endpointId(), items);
        return ResponseEntity.noContent().build();
    }

    public record AckRequest(int exitCode, String output) {}

    public record SoftwareListRequest(UUID endpointId, List<ItemDTO> items) {}

    public record ItemDTO(String name, String id, String version, String updateTo, Boolean isStore, String source) {}
}
