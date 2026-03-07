package dev.pulsermm.commands.infrastructure.security;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ScriptSecretEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    public String encrypt(String plaintext, String kek) throws Exception {
        var key = deriveKey(kek);

        var cipher = Cipher.getInstance(ALGORITHM);
        var iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        var spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        var ciphertext = cipher.doFinal(plaintext.getBytes());

        var combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    public String decrypt(String encrypted, String kek) throws Exception {
        var key = deriveKey(kek);

        var combined = Base64.getDecoder().decode(encrypted);
        var iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

        var ciphertext = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        var cipher = Cipher.getInstance(ALGORITHM);
        var spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        var plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext);
    }

    private SecretKey deriveKey(String kek) {
        var keyBytes = kek.getBytes();
        if (keyBytes.length < 16) {
            throw new IllegalArgumentException("KEK must be at least 16 bytes");
        }
        var keyMaterial = new byte[32];
        System.arraycopy(keyBytes, 0, keyMaterial, 0, Math.min(keyBytes.length, 32));
        return new SecretKeySpec(keyMaterial, 0, 32, "AES");
    }
}
