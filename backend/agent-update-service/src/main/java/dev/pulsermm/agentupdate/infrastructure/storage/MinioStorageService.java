package dev.pulsermm.agentupdate.infrastructure.storage;

import io.minio.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Service
public class MinioStorageService {

    private final MinioClient minio;
    private final String bucket;

    public MinioStorageService(MinioClient minio, @Value("${pulse.minio.bucket}") String bucket) {
        this.minio = minio;
        this.bucket = bucket;
    }

    public UploadResult upload(String objectKey, InputStream data, long size, String contentType) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // wrap stream to compute hash while uploading
            var hashingStream = new DigestInputStream(data, digest);

            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(hashingStream, size, -1)
                    .contentType(contentType)
                    .build());

            String sha256 = HexFormat.of().formatHex(digest.digest());
            return new UploadResult(sha256, size);
        } catch (Exception e) {
            throw new StorageException("Failed to upload to MinIO: " + e.getMessage(), e);
        }
    }

    public String presignDownloadUrl(String objectKey) {
        try {
            return minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .method(Method.GET)
                    .expiry(10, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to presign URL: " + e.getMessage(), e);
        }
    }

    public record UploadResult(String sha256, long sizeBytes) {}

    // minimal DigestInputStream wrapper (java.security.DigestInputStream exists but needs import)
    private static class DigestInputStream extends java.security.DigestInputStream {
        DigestInputStream(InputStream stream, MessageDigest digest) {
            super(stream, digest);
            on(true);
        }
    }
}
