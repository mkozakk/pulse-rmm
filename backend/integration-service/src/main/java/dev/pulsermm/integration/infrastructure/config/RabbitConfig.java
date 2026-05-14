package dev.pulsermm.integration.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.common.events.DomainEventPublisher;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    static final String WEBHOOK_DISPATCH_QUEUE = "webhook.dispatch";

    @Bean
    public Queue webhookDispatchQueue() {
        return new Queue(WEBHOOK_DISPATCH_QUEUE, true);
    }

    @Bean
    public Binding webhookDispatchBinding(Queue webhookDispatchQueue, TopicExchange pulseEventsExchange) {
        return BindingBuilder.bind(webhookDispatchQueue).to(pulseEventsExchange).with("#");
    }

    @Bean
    public Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        return factory;
    }
}
