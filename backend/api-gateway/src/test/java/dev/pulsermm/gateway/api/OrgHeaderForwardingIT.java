package dev.pulsermm.gateway.api;

import com.sun.net.httpserver.HttpServer;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.StringWriter;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// Proves the gateway's OrgContextFilter + Spring Cloud Gateway actually forward the trusted
// X-User-Org-Id header downstream, and that a client-supplied value is overridden / dropped.
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "grpc.server.port=0")
class OrgHeaderForwardingIT {

    private static final String ORG_ID = "33333333-3333-3333-3333-333333333333";
    private static final String SUBJECT = "11111111-1111-1111-1111-111111111111";

    @TempDir
    static Path certDir;

    static final HttpServer stub;
    static final AtomicReference<String> receivedOrgHeader = new AtomicReference<>();

    static {
        try {
            stub = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
        stub.createContext("/", exchange -> {
            receivedOrgHeader.set(exchange.getRequestHeaders().getFirst("X-User-Org-Id"));
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        stub.start();
    }

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.0-alpine").withExposedPorts(6379);

    @TestConfiguration
    static class TestDecoderConfig {
        // The bearer token value carries the scenario: "global" -> no org claim; otherwise it IS the org id.
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                Jwt.Builder b = Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(SUBJECT)
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600));
                if (!"global".equals(token)) {
                    b.claim("org_id", token);
                }
                return b.build();
            };
        }
    }

    @BeforeAll
    static void writeCertBundle() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();

        X500Name name = new X500Name("CN=api-gateway-test");
        Date notBefore = new Date(System.currentTimeMillis() - 60_000L);
        Date notAfter = new Date(System.currentTimeMillis() + 86_400_000L);
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            name, BigInteger.ONE, notBefore, notAfter, name, kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(kp.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

        Files.writeString(certDir.resolve("server.crt"), toPem("CERTIFICATE", cert.getEncoded()));
        Files.writeString(certDir.resolve("ca.crt"), toPem("CERTIFICATE", cert.getEncoded()));
        Files.writeString(certDir.resolve("server.key"), toPem("PRIVATE KEY", kp.getPrivate().getEncoded()));
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("pulse.mtls.cert-dir", () -> certDir.toString());
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // The /api/sessions route forwards to ENDPOINT_SERVICE_URL — point it at our stub.
        registry.add("ENDPOINT_SERVICE_URL", () -> "http://localhost:" + stub.getAddress().getPort());
    }

    @Value("${local.server.port}")
    int port;

    @Test
    void injectsTrustedOrgHeaderAndOverridesSpoofedValue() throws Exception {
        receivedOrgHeader.set(null);
        HttpResponse<String> response = call(ORG_ID, "99999999-9999-9999-9999-999999999999");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(receivedOrgHeader.get()).isEqualTo(ORG_ID);
    }

    @Test
    void globalAdminGetsNoOrgHeader() throws Exception {
        receivedOrgHeader.set(null);
        HttpResponse<String> response = call("global", "88888888-8888-8888-8888-888888888888");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(receivedOrgHeader.get()).isNull();
    }

    private HttpResponse<String> call(String bearer, String spoofedOrg) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/sessions/ping"))
            .header("Authorization", "Bearer " + bearer)
            .header("X-User-Org-Id", spoofedOrg)
            .GET()
            .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    static String toPem(String type, byte[] der) throws Exception {
        try (StringWriter sw = new StringWriter(); JcaPEMWriter w = new JcaPEMWriter(sw)) {
            w.writeObject(new org.bouncycastle.util.io.pem.PemObject(type, der));
            w.flush();
            return sw.toString();
        }
    }
}
