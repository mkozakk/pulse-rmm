package dev.pulsermm.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.audit.domain.AuditEvent;
import dev.pulsermm.audit.infrastructure.persistence.AuditEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuditExportService {

    private static final int BATCH_SIZE = 500;

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditExportService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public void streamCsv(UUID userId, UUID endpointId, Instant from, Instant to, OutputStream out) throws IOException {
        try (var writer = new PrintWriter(out)) {
            writer.println("id,user_id,username,permission_used,action,endpoint_id,payload,created_at");
            int page = 0;
            org.springframework.data.domain.Page<AuditEvent> batch;
            do {
                batch = repository.findFiltered(userId, endpointId, from, to, PageRequest.of(page++, BATCH_SIZE));
                for (var event : batch) {
                    writer.println(toCsvLine(event));
                }
                writer.flush();
            } while (batch.hasNext());
        }
    }

    @Transactional(readOnly = true)
    public void streamNdjson(UUID userId, UUID endpointId, Instant from, Instant to, OutputStream out) throws IOException {
        int page = 0;
        org.springframework.data.domain.Page<AuditEvent> batch;
        do {
            batch = repository.findFiltered(userId, endpointId, from, to, PageRequest.of(page++, BATCH_SIZE));
            for (var event : batch) {
                out.write(objectMapper.writeValueAsBytes(toMap(event)));
                out.write('\n');
            }
            out.flush();
        } while (batch.hasNext());
    }

    private String toCsvLine(AuditEvent e) {
        return String.join(",",
            csvEscape(e.getId()),
            csvEscape(e.getUserId()),
            csvEscape(e.getUsername()),
            csvEscape(e.getPermissionUsed()),
            csvEscape(e.getAction()),
            csvEscape(e.getEndpointId()),
            csvEscape(e.getPayload()),
            csvEscape(e.getCreatedAt())
        );
    }

    private String csvEscape(Object value) {
        if (value == null) return "";
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    private java.util.Map<String, Object> toMap(AuditEvent e) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", e.getId());
        map.put("user_id", e.getUserId());
        map.put("username", e.getUsername());
        map.put("permission_used", e.getPermissionUsed());
        map.put("action", e.getAction());
        map.put("endpoint_id", e.getEndpointId());
        map.put("payload", e.getPayload());
        map.put("created_at", e.getCreatedAt());
        return map;
    }
}
