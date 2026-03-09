package dev.pulsermm.integration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignerTest {

    @Test
    void signReturnsValidSignature() {
        var signer = new HmacSigner();
        byte[] body = "test payload".getBytes(StandardCharsets.UTF_8);
        byte[] secret = "my-secret".getBytes(StandardCharsets.UTF_8);

        var signature = signer.sign(body, secret);

        assertThat(signature).startsWith("sha256=");
        assertThat(signature.length()).isGreaterThan(10);
    }

    @Test
    void sameInputProducesSameSignature() {
        var signer = new HmacSigner();
        byte[] body = "test payload".getBytes(StandardCharsets.UTF_8);
        byte[] secret = "my-secret".getBytes(StandardCharsets.UTF_8);

        var sig1 = signer.sign(body, secret);
        var sig2 = signer.sign(body, secret);

        assertThat(sig1).isEqualTo(sig2);
    }

    @Test
    void differentBodyProducesDifferentSignature() {
        var signer = new HmacSigner();
        byte[] secret = "my-secret".getBytes(StandardCharsets.UTF_8);

        var sig1 = signer.sign("payload1".getBytes(StandardCharsets.UTF_8), secret);
        var sig2 = signer.sign("payload2".getBytes(StandardCharsets.UTF_8), secret);

        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    void differentSecretProducesDifferentSignature() {
        var signer = new HmacSigner();
        byte[] body = "test payload".getBytes(StandardCharsets.UTF_8);

        var sig1 = signer.sign(body, "secret1".getBytes(StandardCharsets.UTF_8));
        var sig2 = signer.sign(body, "secret2".getBytes(StandardCharsets.UTF_8));

        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    void emptyBodyWorks() {
        var signer = new HmacSigner();
        byte[] body = new byte[0];
        byte[] secret = "secret".getBytes(StandardCharsets.UTF_8);

        var signature = signer.sign(body, secret);

        assertThat(signature).startsWith("sha256=");
    }

    @Test
    void longBodyWorks() {
        var signer = new HmacSigner();
        byte[] body = "x".repeat(10000).getBytes(StandardCharsets.UTF_8);
        byte[] secret = "secret".getBytes(StandardCharsets.UTF_8);

        var signature = signer.sign(body, secret);

        assertThat(signature).startsWith("sha256=");
    }
}
