package dev.pulsermm.agentupdate.infrastructure.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.credentials.IamAwsProvider;
import io.minio.credentials.Provider;
import io.minio.credentials.StaticProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Value("${pulse.minio.endpoint}")
    private String endpoint;

    @Value("${pulse.minio.access-key}")
    private String accessKey;

    @Value("${pulse.minio.secret-key}")
    private String secretKey;

    @Value("${pulse.minio.bucket}")
    private String bucket;

    @Value("${pulse.minio.region}")
    private String region;

    @Bean
    public MinioClient minioClient() {
        var builder = MinioClient.builder()
                .endpoint(endpoint)
                .credentialsProvider(credentialsProvider(accessKey, secretKey));
        if (!region.isBlank()) {
            builder.region(region);
        }
        return builder.build();
    }

    // on EC2 the keys are left empty and credentials come from the instance role
    static Provider credentialsProvider(String accessKey, String secretKey) {
        if (accessKey.isBlank()) {
            return new IamAwsProvider(null, null);
        }
        return new StaticProvider(accessKey, secretKey, null);
    }

    @PostConstruct
    public void ensureBucketExists() {
        try {
            var client = minioClient();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("Could not ensure MinIO bucket exists: {}", e.getMessage());
        }
    }
}
