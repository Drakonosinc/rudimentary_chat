package chat.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Authenticated symmetric encryption engine using AES-GCM (128-bit key derived via SHA-256)
 * compatible with standard Java 8+ JCE without requiring unlimited policy patches.
 */
public final class CryptoEngine {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CryptoEngine() {
    }

    private static SecretKeySpec deriveKey(String passphrase) throws Exception {
        String effective = (passphrase == null || passphrase.trim().isEmpty())
                ? "default-rudimentary-chat-key"
                : passphrase.trim();
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] digest = sha.digest(effective.getBytes(StandardCharsets.UTF_8));
        byte[] key128 = Arrays.copyOf(digest, 16);
        return new SecretKeySpec(key128, "AES");
    }

    public static String encryptText(String plainText, String passphrase) {
        if (plainText == null) {
            return "";
        }
        try {
            byte[] encrypted = encryptBytes(plainText.getBytes(StandardCharsets.UTF_8), passphrase);
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed: " + e.getMessage(), e);
        }
    }

    public static String decryptText(String base64Cipher, String passphrase) {
        if (base64Cipher == null || base64Cipher.isEmpty()) {
            return "";
        }
        try {
            byte[] raw = Base64.getDecoder().decode(base64Cipher);
            byte[] plain = decryptBytes(raw, passphrase);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[🔒 Mensaje cifrado — Clave AES diferente]";
        }
    }

    public static byte[] encryptBytes(byte[] plainBytes, String passphrase) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase), spec);
        byte[] cipherText = cipher.doFinal(plainBytes);

        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
        return combined;
    }

    public static byte[] decryptBytes(byte[] combinedIvAndCipher, String passphrase) throws Exception {
        if (combinedIvAndCipher == null || combinedIvAndCipher.length <= GCM_IV_LENGTH) {
            throw new IllegalArgumentException("Invalid AES-GCM payload");
        }
        byte[] iv = Arrays.copyOfRange(combinedIvAndCipher, 0, GCM_IV_LENGTH);
        byte[] cipherText = Arrays.copyOfRange(combinedIvAndCipher, GCM_IV_LENGTH, combinedIvAndCipher.length);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase), spec);
        return cipher.doFinal(cipherText);
    }
}
