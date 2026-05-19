package dev.pulsermm.ca.application;

import dev.pulsermm.ca.infrastructure.CaKeyEncryptor;
import dev.pulsermm.ca.infrastructure.CaRootRepository;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class CaService {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final CaRootRepository repo;
    private final CaKeyEncryptor encryptor;
    private final String rootCn;

    private volatile X509Certificate rootCert;
    private volatile PrivateKey rootKey;
    private volatile String rootCertPem;

    public CaService(CaRootRepository repo,
                     CaKeyEncryptor encryptor,
                     @Value("${pulse.ca.root-cn}") String rootCn) {
        this.repo = repo;
        this.encryptor = encryptor;
        this.rootCn = rootCn;
    }

    @PostConstruct
    void loadOrInitialize() {
        var existing = repo.findCurrent();
        if (existing.isPresent()) {
            this.rootCertPem = existing.get().certPem();
            this.rootCert = parseCertificate(rootCertPem);
            byte[] privDer = encryptor.decrypt(existing.get().encryptedPrivKey());
            try {
                this.rootKey = parseRsaPrivateKey(privDer);
                return;
            } catch (IllegalStateException e) {
                // Legacy Ed25519 root (pre-RSA switch). Re-bootstrap with a fresh RSA root.
            }
        }
        bootstrapRoot();
    }

    public String signCsr(String csrPem, UUID endpointId, Duration ttl) {
        return signInternal(csrPem, "CN=" + endpointId,
            new GeneralNames(new GeneralName(GeneralName.dNSName, endpointId.toString())),
            ttl, KeyPurposeId.id_kp_clientAuth);
    }

    public String signServerCsr(String csrPem, String commonName, List<String> sanDnsNames, Duration ttl) {
        GeneralName[] sans = sanDnsNames.stream()
            .map(name -> isIpAddress(name)
                ? new GeneralName(GeneralName.iPAddress, name)
                : new GeneralName(GeneralName.dNSName, name))
            .toArray(GeneralName[]::new);
        return signInternal(csrPem, "CN=" + commonName, new GeneralNames(sans), ttl, KeyPurposeId.id_kp_serverAuth);
    }

    private static boolean isIpAddress(String s) {
        return s.matches("^\\d{1,3}(\\.\\d{1,3}){3}$") || s.contains(":");
    }

    private String signInternal(String csrPem, String subjectDn, GeneralNames sans, Duration ttl, KeyPurposeId eku) {
        PKCS10CertificationRequest csr = parseCsr(csrPem);
        PublicKey csrPublicKey = extractPublicKey(csr);

        try {
            Instant now = Instant.now();
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name(rootCert.getSubjectX500Principal().getName()),
                new BigInteger(64, new SecureRandom()),
                Date.from(now.minusSeconds(60)),
                Date.from(now.plus(ttl)),
                new X500Name(subjectDn),
                csrPublicKey
            );
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
            builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(eku));
            builder.addExtension(Extension.subjectAlternativeName, false, sans);

            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(rootKey);
            X509CertificateHolder holder = builder.build(signer);
            X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
            return toPem(cert);
        } catch (Exception e) {
            throw new IllegalStateException("signing CSR", e);
        }
    }

    public String getCaBundle() {
        return rootCertPem;
    }

    private void bootstrapRoot() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();

            Instant now = Instant.now();
            X500Name name = new X500Name("CN=" + rootCn);
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name,
                new BigInteger(64, new SecureRandom()),
                Date.from(now.minusSeconds(60)),
                Date.from(now.plus(Duration.ofDays(3650))),
                name,
                kp.getPublic()
            );
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(kp.getPrivate());
            X509CertificateHolder holder = builder.build(signer);
            X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);

            String certPem = toPem(cert);
            byte[] encryptedPriv = encryptor.encrypt(kp.getPrivate().getEncoded());
            repo.save(UUID.randomUUID(), certPem, encryptedPriv);

            this.rootCert = cert;
            this.rootKey = kp.getPrivate();
            this.rootCertPem = certPem;
        } catch (Exception e) {
            throw new IllegalStateException("bootstrapping CA root", e);
        }
    }

    private static PKCS10CertificationRequest parseCsr(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            if (obj instanceof PKCS10CertificationRequest csr) {
                return csr;
            }
            throw new InvalidCsrException("not a PKCS#10 CSR: " + obj);
        } catch (Exception e) {
            if (e instanceof InvalidCsrException ice) throw ice;
            throw new InvalidCsrException("parsing CSR", e);
        }
    }

    private static PublicKey extractPublicKey(PKCS10CertificationRequest csr) {
        try {
            return new JcaPEMKeyConverter().setProvider("BC").getPublicKey(csr.getSubjectPublicKeyInfo());
        } catch (Exception e) {
            throw new InvalidCsrException("extracting CSR public key", e);
        }
    }

    private static X509Certificate parseCertificate(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            if (obj instanceof X509CertificateHolder holder) {
                return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
            }
            throw new IllegalStateException("not a certificate: " + obj);
        } catch (Exception e) {
            throw new IllegalStateException("parsing CA cert", e);
        }
    }

    private static PrivateKey parseRsaPrivateKey(byte[] der) {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("parsing CA private key", e);
        }
    }

    private static String toPem(Object obj) {
        try (StringWriter sw = new StringWriter();
             JcaPEMWriter w = new JcaPEMWriter(sw)) {
            w.writeObject(obj);
            w.flush();
            return sw.toString();
        } catch (Exception e) {
            throw new IllegalStateException("encoding PEM", e);
        }
    }
}
