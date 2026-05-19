package dev.pulsermm.gateway.infrastructure.mtls;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "pulse.mtls.enabled", havingValue = "true")
public class GatewayCertBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(GatewayCertBootstrap.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final String caBaseUrl;
    private final String commonName;
    private final List<String> sans;
    private final Path certPath;
    private final Path keyPath;
    private final Path caPath;

    public GatewayCertBootstrap(
            @Value("${pulse.ca.url:http://ca-service:8089}") String caBaseUrl,
            @Value("${pulse.mtls.gateway-cn:api-gateway}") String commonName,
            @Value("${pulse.mtls.gateway-sans:api-gateway,localhost,127.0.0.1}") String[] sans,
            @Value("${pulse.mtls.cert-dir:/var/lib/api-gateway/tls}") String dir) {
        this.caBaseUrl = caBaseUrl;
        this.commonName = commonName;
        this.sans = List.of(sans);
        this.certPath = Path.of(dir, "server.crt");
        this.keyPath = Path.of(dir, "server.key");
        this.caPath = Path.of(dir, "ca.crt");
    }

    public Bundle ensure() throws Exception {
        Files.createDirectories(certPath.getParent());
        if (Files.exists(certPath) && Files.exists(keyPath) && Files.exists(caPath)) {
            logger.info("Reusing existing gateway server cert at {}", certPath);
            return new Bundle(certPath.toFile(), keyPath.toFile(), caPath.toFile());
        }

        logger.info("Bootstrapping gateway server cert via ca-service at {}", caBaseUrl);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();
        String csrPem = buildCsr(kp);

        RestClient client = RestClient.builder().baseUrl(caBaseUrl).build();
        Map<?, ?> response = client.post()
            .uri("/internal/ca/server-certs")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "csrPem", csrPem,
                "commonName", commonName,
                "sanDnsNames", sans
            ))
            .retrieve()
            .body(Map.class);

        if (response == null) throw new IllegalStateException("ca-service returned empty body");
        String certPem = (String) response.get("certPem");
        String caBundle = (String) response.get("caBundlePem");

        Files.writeString(certPath, certPem);
        Files.writeString(caPath, caBundle);
        Files.writeString(keyPath, toPkcs8Pem(kp.getPrivate()));

        logger.info("Gateway server cert written to {}", certPath);
        return new Bundle(certPath.toFile(), keyPath.toFile(), caPath.toFile());
    }

    private String buildCsr(KeyPair kp) throws Exception {
        JcaPKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(
            new X500Name("CN=" + commonName), kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(kp.getPrivate());
        PKCS10CertificationRequest csr = builder.build(signer);
        try (StringWriter sw = new StringWriter(); JcaPEMWriter w = new JcaPEMWriter(sw)) {
            w.writeObject(csr);
            w.flush();
            return sw.toString();
        }
    }

    private String toPkcs8Pem(PrivateKey key) {
        return "-----BEGIN PRIVATE KEY-----\n"
            + java.util.Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
    }

    public record Bundle(File certFile, File keyFile, File caFile) {}
}
