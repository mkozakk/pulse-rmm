package dev.pulsermm.audit.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    static final String QUEUE = "audit.events.persist";
    static final String EXCHANGE = "audit.events";

    @Bean
    public Queue auditPersistQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public FanoutExchange auditExchange() {
        return new FanoutExchange(EXCHANGE, true, false);
    }

    @Bean
    public Binding auditBinding(Queue auditPersistQueue, FanoutExchange auditExchange) {
        return BindingBuilder.bind(auditPersistQueue).to(auditExchange);
    }
}
