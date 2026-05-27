package dev.pulsermm.gateway.infrastructure.mtls;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayCertBootstrapTest {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void freshCertDoesNotNeedRenewal(@TempDir Path tmp) throws Exception {
        Instant now = Instant.parse("2026-05-01T00:00:00Z");
        Path certPath = writeCert(tmp, now, now.plus(Duration.ofHours(24)));

        assertThat(GatewayCertBootstrap.shouldRenew(certPath, now)).isFalse();
    }

    @Test
    void certPast50PercentTtlNeedsRenewal(@TempDir Path tmp) throws Exception {
        Instant issuedAt = Instant.parse("2026-05-01T00:00:00Z");
        Instant expiresAt = issuedAt.plus(Duration.ofHours(24));
        Path certPath = writeCert(tmp, issuedAt, expiresAt);

        Instant past50 = issuedAt.plus(Duration.ofHours(13));
        assertThat(GatewayCertBootstrap.shouldRenew(certPath, past50)).isTrue();
    }

    @Test
    void unparseableCertNeedsRenewal(@TempDir Path tmp) throws Exception {
        Path certPath = tmp.resolve("garbage.crt");
        Files.writeString(certPath, "not a pem");

        assertThat(GatewayCertBootstrap.shouldRenew(certPath, Instant.now())).isTrue();
    }

    private static Path writeCert(Path dir, Instant notBefore, Instant notAfter) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();

        X500Name name = new X500Name("CN=test");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            name, BigInteger.ONE,
            Date.from(notBefore), Date.from(notAfter),
            name, kp.getPublic()
        );
        var signer = new JcaContentSignerBuilder("SHA256withECDSA").build(kp.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);

        Path certPath = dir.resolve("server.crt");
        try (var w = Files.newBufferedWriter(certPath); JcaPEMWriter pw = new JcaPEMWriter(w)) {
            pw.writeObject(cert);
        }
        return certPath;
    }
}
