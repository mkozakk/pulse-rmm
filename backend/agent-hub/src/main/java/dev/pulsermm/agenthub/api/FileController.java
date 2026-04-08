package dev.pulsermm.agenthub.api;

import dev.pulsermm.agenthub.infrastructure.file.FileTransferRegistry;
import dev.pulsermm.agenthub.infrastructure.grpc.AgentRegistry;
import dev.pulsermm.proto.v1.DirEntry;
import dev.pulsermm.proto.v1.FileDownloadCommand;
import dev.pulsermm.proto.v1.FileUploadCommand;
import dev.pulsermm.proto.v1.GatewayCommand;
import dev.pulsermm.proto.v1.ListDirCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Tag(name = "File Browser", description = "Browse, download, and upload files on managed endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/files/{endpointId}")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    private static final long LIST_TIMEOUT_SECONDS = 15;
    private static final long TRANSFER_TIMEOUT_MINUTES = 10;

    private final AgentRegistry agents;
    private final FileTransferRegistry transfers;
    private final PermissionGuard guard;

    public FileController(AgentRegistry agents, FileTransferRegistry transfers, PermissionGuard guard) {
        this.agents = agents;
        this.transfers = transfers;
        this.guard = guard;
    }

    @Operation(summary = "List directory on endpoint")
    @ApiResponse(responseCode = "200", description = "Directory listing returned")
    @ApiResponse(responseCode = "404", description = "Agent not connected")
    @ApiResponse(responseCode = "504", description = "Agent did not respond within 15 seconds")
    @GetMapping
    public ResponseEntity<?> list(@PathVariable UUID endpointId,
                                  @Parameter(description = "Directory path on the endpoint") @RequestParam(required = false, defaultValue = "") String path) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!guard.canBrowseFiles(auth, endpointId.toString())) {
            return ResponseEntity.status(403).body(problem("Forbidden"));
        }
        var sink = agents.get(endpointId).orElse(null);
        if (sink == null) return ResponseEntity.status(404).body(problem("agent not connected"));

        String requestId = UUID.randomUUID().toString();
        var future = transfers.registerDirRequest(requestId);
        sink.onNext(GatewayCommand.newBuilder()
            .setListDir(ListDirCommand.newBuilder().setRequestId(requestId).setPath(path).build())
            .build());

        try {
            var resp = future.get(LIST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!resp.error().isEmpty()) return ResponseEntity.status(400).body(problem(resp.error()));
            List<Map<String, Object>> entries = resp.entries().stream().map(FileController::toJson).toList();
            return ResponseEntity.ok(Map.of("path", resp.path(), "entries", entries));
        } catch (TimeoutException e) {
            return ResponseEntity.status(504).body(problem("agent did not respond"));
        }
    }

    @Operation(summary = "Download a file from endpoint")
    @ApiResponse(responseCode = "200", description = "File stream started")
    @ApiResponse(responseCode = "404", description = "Agent not connected")
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID endpointId,
                                                        @Parameter(description = "Absolute path to the file") @RequestParam String path) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!guard.canBrowseFiles(auth, endpointId.toString())) {
            return ResponseEntity.status(403).build();
        }
        var sink = agents.get(endpointId).orElse(null);
        if (sink == null) return ResponseEntity.status(404).build();

        String transferId = transfers.newId();
        LinkedBlockingQueue<byte[]> queue = transfers.openDownload(transferId);
        sink.onNext(GatewayCommand.newBuilder()
            .setFileDownload(FileDownloadCommand.newBuilder().setTransferId(transferId).setPath(path).build())
            .build());

        String filename = Paths.get(path).getFileName().toString();
        InputStream body = new QueueInputStream(queue);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new InputStreamResource(body));
    }

    @Operation(summary = "Upload a file to endpoint")
    @ApiResponse(responseCode = "200", description = "File uploaded")
    @ApiResponse(responseCode = "404", description = "Agent not connected")
    @ApiResponse(responseCode = "504", description = "Agent did not complete upload")
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@PathVariable UUID endpointId,
                                    @Parameter(description = "Destination path on the endpoint") @RequestParam String path,
                                    @RequestParam("file") MultipartFile file) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!guard.canBrowseFiles(auth, endpointId.toString())) {
            return ResponseEntity.status(403).body(problem("Forbidden"));
        }
        var sink = agents.get(endpointId).orElse(null);
        if (sink == null) return ResponseEntity.status(404).body(problem("agent not connected"));

        String transferId = transfers.newId();
        LinkedBlockingQueue<byte[]> queue = transfers.openUpload(transferId);
        sink.onNext(GatewayCommand.newBuilder()
            .setFileUpload(FileUploadCommand.newBuilder().setTransferId(transferId).setDestPath(path).build())
            .build());

        try (InputStream in = file.getInputStream()) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                byte[] copy = new byte[n];
                System.arraycopy(buf, 0, copy, 0, n);
                queue.put(copy);
            }
        } finally {
            queue.put(FileTransferRegistry.EOF);
        }

        try {
            var outcome = transfers.doneFuture(transferId).get(TRANSFER_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (outcome.error() != null && !outcome.error().isEmpty()) {
                return ResponseEntity.status(500).body(problem(outcome.error()));
            }
            return ResponseEntity.ok(Map.of("bytes", outcome.bytes()));
        } catch (TimeoutException e) {
            return ResponseEntity.status(504).body(problem("agent did not complete upload"));
        }
    }

    private static Map<String, Object> toJson(DirEntry e) {
        return Map.of(
            "name", e.getName(),
            "path", e.getPath(),
            "isDir", e.getIsDir(),
            "size", e.getSize(),
            "modified", e.getModified()
        );
    }

    private static Map<String, Object> problem(String msg) {
        return Map.of("title", "File operation failed", "detail", msg);
    }

    private static final class QueueInputStream extends InputStream {
        private final LinkedBlockingQueue<byte[]> queue;
        private byte[] current;
        private int pos;
        private boolean closed;

        QueueInputStream(LinkedBlockingQueue<byte[]> queue) { this.queue = queue; }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n == -1 ? -1 : (one[0] & 0xff);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (closed) return -1;
            if (current == null || pos >= current.length) {
                try {
                    current = queue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
                pos = 0;
                if (current == FileTransferRegistry.EOF) {
                    closed = true;
                    return -1;
                }
            }
            int remaining = current.length - pos;
            int n = Math.min(remaining, len);
            System.arraycopy(current, pos, b, off, n);
            pos += n;
            return n;
        }
    }
}
