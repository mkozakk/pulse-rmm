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
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class ReloadableX509KeyManagerTest {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void reloadPicksUpNewCertOnDisk(@TempDir Path tmp) throws Exception {
        Path certPath = tmp.resolve("server.crt");
        Path keyPath = tmp.resolve("server.key");

        KeyPair first = newKeyPair();
        writeCert(certPath, first, "first");
        writeKey(keyPath, first);

        ReloadableX509KeyManager km = new ReloadableX509KeyManager(certPath, keyPath);
        String alias = km.getServerAliases("EC", null)[0];
        X509Certificate before = km.getCertificateChain(alias)[0];
        assertThat(before.getSubjectX500Principal().getName()).contains("CN=first");

        KeyPair second = newKeyPair();
        writeCert(certPath, second, "second");
        writeKey(keyPath, second);
        km.reload();

        X509Certificate after = km.getCertificateChain(alias)[0];
        assertThat(after.getSubjectX500Principal().getName()).contains("CN=second");
        assertThat(after).isNotEqualTo(before);
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        return kpg.generateKeyPair();
    }

    private static void writeCert(Path path, KeyPair kp, String cn) throws Exception {
        Instant now = Instant.now();
        X500Name name = new X500Name("CN=" + cn);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            name, BigInteger.ONE,
            Date.from(now.minusSeconds(60)), Date.from(now.plus(Duration.ofHours(24))),
            name, kp.getPublic()
        );
        var signer = new JcaContentSignerBuilder("SHA256withECDSA").build(kp.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
        try (var w = Files.newBufferedWriter(path); JcaPEMWriter pw = new JcaPEMWriter(w)) {
            pw.writeObject(cert);
        }
    }

    private static void writeKey(Path path, KeyPair kp) throws Exception {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(kp.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
        Files.writeString(path, pem);
    }
}
