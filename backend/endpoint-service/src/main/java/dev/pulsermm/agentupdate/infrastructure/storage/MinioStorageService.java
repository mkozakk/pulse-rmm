package dev.pulsermm.agentupdate.infrastructure.storage;

import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class MinioStorageService {

    private final MinioClient minio;
    private final String bucket;

    public MinioStorageService(MinioClient minio,
                               @Value("${pulse.minio.bucket}") String bucket) {
        this.minio = minio;
        this.bucket = bucket;
    }

    public UploadResult upload(String objectKey, InputStream data, long size, String contentType) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            var hashingStream = new DigestInputStream(data, digest);
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(hashingStream, size, -1)
                    .contentType(contentType)
                    .build());
            return new UploadResult(HexFormat.of().formatHex(digest.digest()), size);
        } catch (Exception e) {
            throw new StorageException("Failed to upload to MinIO: " + e.getMessage(), e);
        }
    }

    public InputStream download(String objectKey) {
        try {
            return minio.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to download from MinIO: " + e.getMessage(), e);
        }
    }

    public record UploadResult(String sha256, long sizeBytes) {}

    private static class DigestInputStream extends java.security.DigestInputStream {
        DigestInputStream(InputStream stream, MessageDigest digest) {
            super(stream, digest);
            on(true);
        }
    }
}
