package dev.pulsermm.gateway;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;

@SpringBootTest(properties = {
    "pulse.jwt.secret=test-secret-key-that-is-long-enough-for-hs256",
    "grpc.server.port=0"
})
class GatewayRoutingTest {

    @TempDir
    static Path certDir;

    @DynamicPropertySource
    static void mtlsCertDir(DynamicPropertyRegistry registry) {
        registry.add("pulse.mtls.cert-dir", () -> certDir.toString());
    }

    @BeforeAll
    static void writeSelfSignedBundle() throws Exception {
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

    private static String toPem(String type, byte[] der) throws Exception {
        try (StringWriter sw = new StringWriter(); JcaPEMWriter w = new JcaPEMWriter(sw)) {
            w.writeObject(new org.bouncycastle.util.io.pem.PemObject(type, der));
            w.flush();
            return sw.toString();
        }
    }

    @Test
    void contextLoads() {
    }
}
