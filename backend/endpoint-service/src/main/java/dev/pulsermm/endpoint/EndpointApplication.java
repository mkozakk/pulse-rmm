package dev.pulsermm.endpoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "dev.pulsermm.endpoint",
    "dev.pulsermm.enrolment",
    "dev.pulsermm.agentupdate",
    "dev.pulsermm.remote"
})
@EnableJpaRepositories(basePackages = {
    "dev.pulsermm.endpoint",
    "dev.pulsermm.enrolment",
    "dev.pulsermm.agentupdate",
    "dev.pulsermm.remote"
})
@EntityScan(basePackages = {
    "dev.pulsermm.endpoint",
    "dev.pulsermm.enrolment",
    "dev.pulsermm.agentupdate",
    "dev.pulsermm.remote"
})
@EnableScheduling
public class EndpointApplication {

    public static void main(String[] args) {
        SpringApplication.run(EndpointApplication.class, args);
    }
}
