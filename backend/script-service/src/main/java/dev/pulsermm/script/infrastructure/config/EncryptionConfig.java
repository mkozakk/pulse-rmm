package dev.pulsermm.script.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EncryptionConfig {

    @Value("${pulse.script.secret-kek}")
    private String scriptSecretKek;

    @Bean(name = "scriptSecretKek")
    public String scriptSecretKek() {
        return scriptSecretKek;
    }
}
