package dev.pulsermm.ca.application;

import dev.pulsermm.ca.CaApplication;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@Testcontainers
@SpringBootTest(classes = CaApplication.class)
@ActiveProfiles("test")
class CaServiceIT {

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
    private CaService caService;

    @BeforeAll
    static void setupBc() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void signsCsrWithEnforcedCnAndTtl() throws Exception {
        UUID endpointId = UUID.randomUUID();
        String csrPem = generateCsr("CN=ignored-by-server");
        Duration ttl = Duration.ofHours(24);

        Instant beforeSign = Instant.now();
        String certPem = caService.signCsr(csrPem, endpointId, ttl);
        Instant afterSign = Instant.now();

        X509Certificate cert = parsePem(certPem);
        assertThat(cert.getSubjectX500Principal().getName()).contains("CN=" + endpointId);
        assertThat(cert.getNotBefore().toInstant()).isBeforeOrEqualTo(afterSign);
        assertThat(cert.getNotAfter().toInstant())
            .isCloseTo(beforeSign.plus(ttl), within(10L, ChronoUnit.SECONDS));
    }

    @Test
    void signedCertChainsToCaBundle() throws Exception {
        UUID endpointId = UUID.randomUUID();
        String csrPem = generateCsr("CN=ignored");

        String certPem = caService.signCsr(csrPem, endpointId, Duration.ofHours(24));
        String bundlePem = caService.getCaBundle();

        X509Certificate cert = parsePem(certPem);
        X509Certificate ca = parsePem(bundlePem);
        cert.verify(ca.getPublicKey());
    }

    @Test
    void caBundleReturnsRootCert() {
        String bundle = caService.getCaBundle();
        assertThat(bundle).startsWith("-----BEGIN CERTIFICATE-----");
        assertThat(bundle).contains("-----END CERTIFICATE-----");
    }

    @Test
    void serverCsrSigningEmitsServerAuthEkuAndSans() throws Exception {
        String csrPem = generateCsr("CN=anything");
        String certPem = caService.signServerCsr(csrPem, "api-gateway",
            java.util.List.of("api-gateway", "localhost", "127.0.0.1"),
            Duration.ofHours(24));

        X509Certificate cert = parsePem(certPem);
        assertThat(cert.getSubjectX500Principal().getName()).contains("CN=api-gateway");
        assertThat(cert.getExtendedKeyUsage()).contains("1.3.6.1.5.5.7.3.1"); // serverAuth
        java.util.Collection<java.util.List<?>> sans = cert.getSubjectAlternativeNames();
        assertThat(sans).isNotNull();
        java.util.Set<String> sanValues = new java.util.HashSet<>();
        for (java.util.List<?> entry : sans) {
            int type = (Integer) entry.get(0);
            if (type == 2 || type == 7) sanValues.add((String) entry.get(1));
        }
        assertThat(sanValues).containsExactlyInAnyOrder("api-gateway", "localhost", "127.0.0.1");
    }

    @Test
    void rejectsMalformedCsr() {
        assertThatThrownBy(() ->
            caService.signCsr("not a real CSR", UUID.randomUUID(), Duration.ofHours(24)))
            .isInstanceOf(InvalidCsrException.class);
    }

    private static String generateCsr(String subject) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        JcaPKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(
            new X500Name(subject), kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("Ed25519").build(kp.getPrivate());
        PKCS10CertificationRequest csr = builder.build(signer);
        StringWriter sw = new StringWriter();
        try (org.bouncycastle.openssl.jcajce.JcaPEMWriter w = new org.bouncycastle.openssl.jcajce.JcaPEMWriter(sw)) {
            w.writeObject(csr);
        }
        return sw.toString();
    }

    private static X509Certificate parsePem(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            if (obj instanceof org.bouncycastle.cert.X509CertificateHolder holder) {
                return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(holder);
            }
            throw new IllegalStateException("not a cert: " + obj);
        }
    }

}
