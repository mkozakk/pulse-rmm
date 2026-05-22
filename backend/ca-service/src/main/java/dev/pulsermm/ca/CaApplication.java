package dev.pulsermm.ca;

import dev.pulsermm.common.audit.AuditAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;

@SpringBootApplication(exclude = {RabbitAutoConfiguration.class, AuditAutoConfiguration.class})
public class CaApplication {
    public static void main(String[] args) {
        SpringApplication.run(CaApplication.class, args);
    }
}
