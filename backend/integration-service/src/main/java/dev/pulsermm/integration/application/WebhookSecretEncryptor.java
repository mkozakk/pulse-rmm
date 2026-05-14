package dev.pulsermm.integration.application;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

public class WebhookSecretEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    static final String KEK_ID = "kek-v1";

    private final byte[] keyMaterial;

    public WebhookSecretEncryptor(String kek) {
        var raw = kek.getBytes();
        if (raw.length < 16) {
            throw new IllegalArgumentException("WEBHOOK_SECRET_KEK must be at least 16 bytes");
        }
        keyMaterial = new byte[32];
        System.arraycopy(raw, 0, keyMaterial, 0, Math.min(raw.length, 32));
    }

    public byte[] encrypt(String plaintext) {
        try {
            var key = new SecretKeySpec(keyMaterial, "AES");
            var iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            var ciphertext = cipher.doFinal(plaintext.getBytes());

            var result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("encryption failed", e);
        }
    }

    public String decrypt(byte[] ciphertext) {
        try {
            var key = new SecretKeySpec(keyMaterial, "AES");
            var iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(ciphertext, 0, iv, 0, GCM_IV_LENGTH);

            var data = new byte[ciphertext.length - GCM_IV_LENGTH];
            System.arraycopy(ciphertext, GCM_IV_LENGTH, data, 0, data.length);

            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(data));
        } catch (Exception e) {
            throw new RuntimeException("decryption failed", e);
        }
    }
}
