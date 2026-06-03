package dev.pulsermm.agenthub.infrastructure.file;

import dev.pulsermm.proto.v1.DirEntry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class FileTransferRegistry {

    public static final byte[] EOF = new byte[0];

    private final Map<String, CompletableFuture<ListDirResponse>> dirRequests = new ConcurrentHashMap<>();
    private final Map<String, LinkedBlockingQueue<byte[]>> downloadQueues = new ConcurrentHashMap<>();
    private final Map<String, LinkedBlockingQueue<byte[]>> uploadQueues = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<TransferOutcome>> doneFutures = new ConcurrentHashMap<>();

    public String newId() {
        return UUID.randomUUID().toString();
    }

    public CompletableFuture<ListDirResponse> registerDirRequest(String requestId) {
        CompletableFuture<ListDirResponse> f = new CompletableFuture<>();
        dirRequests.put(requestId, f);
        return f;
    }

    public void completeDir(String requestId, String path, List<DirEntry> entries, String error) {
        CompletableFuture<ListDirResponse> f = dirRequests.remove(requestId);
        if (f != null) f.complete(new ListDirResponse(path, entries, error));
    }

    // Download path: agent → backend → webapp.
    // The agent's FileService.Upload puts chunks into this queue; the webapp's
    // streaming response reads from it.
    public LinkedBlockingQueue<byte[]> openDownload(String transferId) {
        LinkedBlockingQueue<byte[]> q = new LinkedBlockingQueue<>();
        downloadQueues.put(transferId, q);
        doneFutures.put(transferId, new CompletableFuture<>());
        return q;
    }

    public LinkedBlockingQueue<byte[]> downloadQueue(String transferId) {
        return downloadQueues.get(transferId);
    }

    // Upload path: webapp → backend → agent.
    // The webapp's request body puts chunks into this queue; the agent's
    // FileService.Download server-streaming RPC reads from it.
    public LinkedBlockingQueue<byte[]> openUpload(String transferId) {
        LinkedBlockingQueue<byte[]> q = new LinkedBlockingQueue<>();
        uploadQueues.put(transferId, q);
        doneFutures.put(transferId, new CompletableFuture<>());
        return q;
    }

    public LinkedBlockingQueue<byte[]> uploadQueue(String transferId) {
        return uploadQueues.get(transferId);
    }

    public CompletableFuture<TransferOutcome> doneFuture(String transferId) {
        return doneFutures.computeIfAbsent(transferId, k -> new CompletableFuture<>());
    }

    public void completeTransfer(String transferId, long bytes, String error) {
        downloadQueues.remove(transferId);
        uploadQueues.remove(transferId);
        CompletableFuture<TransferOutcome> f = doneFutures.remove(transferId);
        if (f != null) f.complete(new TransferOutcome(bytes, error));
    }

    public record ListDirResponse(String path, List<DirEntry> entries, String error) {}
    public record TransferOutcome(long bytes, String error) {}
}
