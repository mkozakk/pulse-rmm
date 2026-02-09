package dev.pulsermm.gateway.infrastructure.grpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcServiceConfig {
    private static final Logger logger = LoggerFactory.getLogger(GrpcServiceConfig.class);

    public GrpcServiceConfig() {
        logger.info("GrpcServiceConfig initialized");
    }

    @Bean
    public AgentServiceGrpcServer agentServiceGrpcServer() {
        logger.info("Creating AgentServiceGrpcServer bean");
        return new AgentServiceGrpcServer();
    }
}
