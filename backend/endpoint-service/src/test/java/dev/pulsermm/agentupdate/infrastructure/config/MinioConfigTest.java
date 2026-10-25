package dev.pulsermm.agentupdate.infrastructure.config;

import io.minio.credentials.IamAwsProvider;
import io.minio.credentials.StaticProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinioConfigTest {

    @Test
    void usesStaticKeysWhenAccessKeyIsSet() {
        assertThat(MinioConfig.credentialsProvider("pulse", "secret")).isInstanceOf(StaticProvider.class);
    }

    @Test
    void fallsBackToInstanceRoleWhenAccessKeyIsBlank() {
        assertThat(MinioConfig.credentialsProvider("", "")).isInstanceOf(IamAwsProvider.class);
    }
}
