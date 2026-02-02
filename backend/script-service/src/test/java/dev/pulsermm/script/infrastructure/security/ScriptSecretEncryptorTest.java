package dev.pulsermm.script.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScriptSecretEncryptorTest {

    private final ScriptSecretEncryptor encryptor = new ScriptSecretEncryptor();
    private final String kek = "this-is-a-test-key-encryption-key-32-bytes-long";

    @Test
    void encrypt_AndDecrypt_ReturnsOriginalPlaintext() throws Exception {
        var plaintext = "hunter2";

        var encrypted = encryptor.encrypt(plaintext, kek);
        var decrypted = encryptor.decrypt(encrypted, kek);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encrypt_WithDifferentPlaintexts_ProducesDifferentCiphertexts() throws Exception {
        var plaintext1 = "secret1";
        var plaintext2 = "secret2";

        var encrypted1 = encryptor.encrypt(plaintext1, kek);
        var encrypted2 = encryptor.encrypt(plaintext1, kek);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    void encrypt_LongSecret_Works() throws Exception {
        var plaintext = "this is a much longer secret that contains spaces and special chars !@#$%";

        var encrypted = encryptor.encrypt(plaintext, kek);
        var decrypted = encryptor.decrypt(encrypted, kek);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void decrypt_WithWrongKek_Fails() throws Exception {
        var plaintext = "hunter2";
        var encrypted = encryptor.encrypt(plaintext, kek);

        var wrongKek = "wrong-key-that-is-also-32-bytes-long-encryption-key";

        assertThatThrownBy(() -> encryptor.decrypt(encrypted, wrongKek))
                .isInstanceOf(Exception.class);
    }

    @Test
    void decrypt_WithMalformedBase64_Fails() throws Exception {
        var malformedEncrypted = "not-valid-base64!!!";

        assertThatThrownBy(() -> encryptor.decrypt(malformedEncrypted, kek))
                .isInstanceOf(Exception.class);
    }

    @Test
    void encrypt_WithShortKek_Fails() {
        var plaintext = "hunter2";
        var shortKek = "short";

        assertThatThrownBy(() -> encryptor.encrypt(plaintext, shortKek))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 16 bytes");
    }
}
