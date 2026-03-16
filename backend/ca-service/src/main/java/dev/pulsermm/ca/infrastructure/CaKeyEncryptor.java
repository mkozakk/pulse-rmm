package dev.pulsermm.ca.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

@Component
public class CaKeyEncryptor {

    private static final int NONCE_LEN = 12;
    private static final int TAG_LEN_BITS = 128;

    private final byte[] key;
    private final SecureRandom rng = new SecureRandom();

    public CaKeyEncryptor(@Value("${pulse.ca.root-key-encryption-secret}") String secret) {
        this.key = sha256(secret.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LEN];
            rng.nextBytes(nonce);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LEN_BITS, nonce));
            byte[] ct = c.doFinal(plaintext);
            byte[] out = new byte[nonce.length + ct.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(ct, 0, out, nonce.length, ct.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("encrypting CA key", e);
        }
    }

    public byte[] decrypt(byte[] ciphertext) {
        try {
            byte[] nonce = Arrays.copyOfRange(ciphertext, 0, NONCE_LEN);
            byte[] body = Arrays.copyOfRange(ciphertext, NONCE_LEN, ciphertext.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LEN_BITS, nonce));
            return c.doFinal(body);
        } catch (Exception e) {
            throw new IllegalStateException("decrypting CA key", e);
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
