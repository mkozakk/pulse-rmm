package dev.pulsermm.enrolment.application;

import dev.pulsermm.endpoint.EndpointApplication;
import dev.pulsermm.enrolment.application.CertRenewService.RevokedEndpointException;
import dev.pulsermm.enrolment.application.CertRenewService.UnknownEndpointException;
import dev.pulsermm.enrolment.domain.Endpoint;
import dev.pulsermm.enrolment.domain.EndpointRevocation;
import dev.pulsermm.enrolment.domain.Group;
import dev.pulsermm.enrolment.infrastructure.CaClient;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import dev.pulsermm.enrolment.infrastructure.EndpointRevocationRepository;
import dev.pulsermm.enrolment.infrastructure.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(classes = EndpointApplication.class, properties = {
    "pulse.ca.enabled=true"
})
@ActiveProfiles("test")
@Import(CertRenewIT.MockBeans.class)
class CertRenewIT {

    @TestConfiguration
    static class MockBeans {
        @Bean
        RabbitTemplate rabbitTemplate() { return mock(RabbitTemplate.class); }

        @Bean
        @Primary
        CaClient caClient() {
            CaClient stub = mock(CaClient.class);
            when(stub.sign(anyString(), any(UUID.class)))
                .thenReturn(new CaClient.SignedCert("CERT_PEM", "CA_PEM"));
            return stub;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("pulse")
        .withUsername("pulse")
        .withPassword("pulse");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private CertRenewService renewService;
    @Autowired private EndpointRepository endpointRepository;
    @Autowired private EndpointRevocationRepository revocationRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private JdbcTemplate jdbc;

    private UUID endpointId;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM enrolment.endpoint_revocations");
        jdbc.update("DELETE FROM enrolment.endpoints");
        jdbc.update("DELETE FROM enrolment.groups");
        Group g = new Group(UUID.randomUUID(), "g", null);
        groupRepository.save(g);
        endpointId = UUID.randomUUID();
        byte[] pk = new byte[32]; new java.util.Random().nextBytes(pk);
        endpointRepository.save(new Endpoint(endpointId, "host", "linux", "amd64", g.getId(), pk, Instant.now(), Instant.now()));
    }

    @Test
    void renewsCertForEnrolledNonRevokedEndpoint() {
        var res = renewService.renew(endpointId, "----CSR----");
        assertThat(res.certPem()).isEqualTo("CERT_PEM");
        assertThat(res.caBundlePem()).isEqualTo("CA_PEM");
    }

    @Test
    void rejectsRenewalForRevokedEndpoint() {
        revocationRepository.save(new EndpointRevocation(endpointId, Instant.now(), "manual"));
        assertThatThrownBy(() -> renewService.renew(endpointId, "----CSR----"))
            .isInstanceOf(RevokedEndpointException.class);
    }

    @Test
    void rejectsRenewalForUnknownEndpoint() {
        assertThatThrownBy(() -> renewService.renew(UUID.randomUUID(), "----CSR----"))
            .isInstanceOf(UnknownEndpointException.class);
    }

    @Test
    void rejectsEmptyCsr() {
        assertThatThrownBy(() -> renewService.renew(endpointId, ""))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
