package dev.pulsermm.endpoint.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean(initMethod = "migrate")
    public Flyway flywayEnrolment() {
        return Flyway.configure()
            .dataSource(url, username, password)
            .schemas("enrolment")
            .locations("classpath:db/migration/enrolment")
            .defaultSchema("enrolment")
            .baselineOnMigrate(true)
            .load();
    }

    @Bean(initMethod = "migrate")
    public Flyway flywayAgentUpdate() {
        return Flyway.configure()
            .dataSource(url, username, password)
            .schemas("agent_update")
            .locations("classpath:db/migration/agent_update")
            .defaultSchema("agent_update")
            .baselineOnMigrate(true)
            .load();
    }

    @Bean(initMethod = "migrate")
    public Flyway flywayRemote() {
        return Flyway.configure()
            .dataSource(url, username, password)
            .schemas("remote")
            .locations("classpath:db/migration/remote")
            .defaultSchema("remote")
            .baselineOnMigrate(true)
            .load();
    }
}
