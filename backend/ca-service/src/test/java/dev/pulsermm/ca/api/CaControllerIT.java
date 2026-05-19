package dev.pulsermm.ca.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pulsermm.ca.CaApplication;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = CaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaControllerIT {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("pulse")
        .withUsername("pulse")
        .withPassword("pulse");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void signEndpointReturnsCertAndBundle() throws Exception {
        UUID endpointId = UUID.randomUUID();
        String csr = generateCsr();
        var body = Map.of("csrPem", csr, "endpointId", endpointId.toString());

        mvc.perform(post("/internal/ca/sign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.certPem").value(org.hamcrest.Matchers.startsWith("-----BEGIN CERTIFICATE-----")))
            .andExpect(jsonPath("$.caBundlePem").value(org.hamcrest.Matchers.startsWith("-----BEGIN CERTIFICATE-----")));
    }

    @Test
    void bundleEndpointReturnsCaCert() throws Exception {
        mvc.perform(get("/internal/ca/bundle"))
            .andExpect(status().isOk());
    }

    @Test
    void malformedCsrReturns400() throws Exception {
        var body = Map.of("csrPem", "garbage", "endpointId", UUID.randomUUID().toString());

        mvc.perform(post("/internal/ca/sign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    private static String generateCsr() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        JcaPKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(
            new X500Name("CN=pending"), kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("Ed25519").build(kp.getPrivate());
        PKCS10CertificationRequest csr = builder.build(signer);
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) {
            w.writeObject(csr);
        }
        return sw.toString();
    }
}
