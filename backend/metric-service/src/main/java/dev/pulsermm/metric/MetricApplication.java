package dev.pulsermm.metric;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MetricApplication {
    public static void main(String[] args) {
        SpringApplication.run(MetricApplication.class, args);
    }
}
