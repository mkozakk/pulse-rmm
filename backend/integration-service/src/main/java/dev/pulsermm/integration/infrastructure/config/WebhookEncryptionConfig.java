package dev.pulsermm.integration.infrastructure.config;

import dev.pulsermm.integration.application.WebhookSecretEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebhookEncryptionConfig {

    @Value("${pulse.webhooks.kek}")
    private String kek;

    @Bean
    public WebhookSecretEncryptor webhookSecretEncryptor() {
        return new WebhookSecretEncryptor(kek);
    }
}
