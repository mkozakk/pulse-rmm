package dev.pulsermm.alert.infrastructure.config;

import dev.pulsermm.common.events.DomainEventPublisher;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationRabbitConfig {

    static final String QUEUE = "alert.service.notifications";

    @Bean
    public Queue notificationQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding endpointEnrolledBinding(Queue notificationQueue, TopicExchange pulseEventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(pulseEventsExchange).with("endpoint.enrolled");
    }

    @Bean
    public Binding endpointOnlineBinding(Queue notificationQueue, TopicExchange pulseEventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(pulseEventsExchange).with("endpoint.online");
    }

    @Bean
    public Binding endpointOfflineBinding(Queue notificationQueue, TopicExchange pulseEventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(pulseEventsExchange).with("endpoint.offline");
    }

    @Bean
    public Binding auditEventsBinding(Queue notificationQueue, TopicExchange pulseEventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(pulseEventsExchange).with("audit.#");
    }

    @Bean
    public Binding scriptResultBinding(Queue notificationQueue, TopicExchange pulseEventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(pulseEventsExchange).with("script.result");
    }

    @Bean
    public Binding softwareCommandCompletedBinding(Queue notificationQueue, TopicExchange pulseEventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(pulseEventsExchange).with("software.command.completed");
    }
}
