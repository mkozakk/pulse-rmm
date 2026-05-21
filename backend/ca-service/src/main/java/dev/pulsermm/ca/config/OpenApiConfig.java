package dev.pulsermm.ca.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Pulse RMM API — CA")
                .description("Internal certificate authority — CSR signing and CA bundle retrieval")
                .version("1.0.0")
                .contact(new Contact()
                    .name("Pulse RMM")
                    .email("michalkozakk0@gmail.com")))
            .servers(List.of(new Server().url("http://localhost:8080")));
    }
}
