package dev.pulsermm.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@AutoConfiguration
@ConditionalOnClass(RabbitTemplate.class)
@EnableAspectJAutoProxy
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditPublisher auditPublisher(RabbitTemplate rabbitTemplate, Jackson2JsonMessageConverter converter) {
        rabbitTemplate.setMessageConverter(converter);
        return new AuditPublisher(rabbitTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditAspect auditAspect(AuditPublisher publisher, ObjectMapper objectMapper) {
        return new AuditAspect(publisher, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public FanoutExchange auditEventsExchange() {
        return new FanoutExchange("audit.events", true, false);
    }
}
