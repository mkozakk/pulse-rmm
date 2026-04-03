package dev.pulsermm.commands.infrastructure.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScriptDispatchRabbitConfig {

    public static final String QUEUE = "script.dispatch";

    @Bean
    public Queue scriptDispatchQueue() {
        return new Queue(QUEUE, true);
    }
}
