package dev.pulsermm.agenthub.infrastructure.file;

import dev.pulsermm.proto.v1.FileChunk;
import dev.pulsermm.proto.v1.FileDownloadRequest;
import dev.pulsermm.proto.v1.FileServiceGrpc;
import dev.pulsermm.proto.v1.FileTransferAck;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;

@GrpcService
public class FileServiceGrpcServer extends FileServiceGrpc.FileServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(FileServiceGrpcServer.class);

    private final FileTransferRegistry registry;

    public FileServiceGrpcServer(FileTransferRegistry registry) {
        this.registry = registry;
    }

    // Agent uploads file bytes to backend (webapp is downloading from agent).
    @Override
    public StreamObserver<FileChunk> upload(StreamObserver<FileTransferAck> responseObserver) {
        return new StreamObserver<>() {
            private String transferId;
            private LinkedBlockingQueue<byte[]> queue;
            private long total;

            @Override
            public void onNext(FileChunk chunk) {
                if (transferId == null) {
                    transferId = chunk.getTransferId();
                    queue = registry.downloadQueue(transferId);
                    if (queue == null) {
                        responseObserver.onError(Status.NOT_FOUND
                            .withDescription("unknown transfer " + transferId)
                            .asRuntimeException());
                        return;
                    }
                    if (chunk.getData().isEmpty()) return;
                }
                byte[] data = chunk.getData().toByteArray();
                if (data.length > 0) {
                    total += data.length;
                    queue.offer(data);
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.warn("Upload stream error for {}: {}", transferId, t.getMessage());
                if (queue != null) queue.offer(FileTransferRegistry.EOF);
            }

            @Override
            public void onCompleted() {
                if (queue != null) queue.offer(FileTransferRegistry.EOF);
                responseObserver.onNext(FileTransferAck.newBuilder().setBytes(total).build());
                responseObserver.onCompleted();
            }
        };
    }

    // Agent downloads file bytes from backend (webapp is uploading to agent).
    @Override
    public void download(FileDownloadRequest request, StreamObserver<FileChunk> responseObserver) {
        String transferId = request.getTransferId();
        LinkedBlockingQueue<byte[]> queue = registry.uploadQueue(transferId);
        if (queue == null) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription("unknown transfer " + transferId)
                .asRuntimeException());
            return;
        }
        try {
            boolean sentHeader = false;
            while (true) {
                byte[] chunk = queue.take();
                if (chunk == FileTransferRegistry.EOF) break;
                FileChunk.Builder b = FileChunk.newBuilder().setData(ByteString.copyFrom(chunk));
                if (!sentHeader) {
                    b.setTransferId(transferId);
                    sentHeader = true;
                }
                responseObserver.onNext(b.build());
            }
            responseObserver.onCompleted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(Status.CANCELLED.withDescription("interrupted").asRuntimeException());
        }
    }
}
