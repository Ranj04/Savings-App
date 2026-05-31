package security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption for secrets at rest (Plaid access tokens). The key comes from
 * the {@code PLAID_TOKEN_KEY} env var (base64 16/24/32 bytes, or any string which is
 * hashed to 32 bytes). If no key is set, values pass through unencrypted — fine for
 * local dev, and a warning is printed; set the key in any real deployment.
 */
public final class CryptoUtil {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final String PREFIX = "enc:";
    private static final byte[] KEY = loadKey();

    private CryptoUtil() {
    }

    public static boolean isEnabled() {
        return KEY != null;
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null) return null;
        if (KEY == null) return plaintext; // dev fallback
        try {
            byte[] iv = new byte[IV_LEN];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public static String decrypt(String stored) {
        if (stored == null) return null;
        if (!stored.startsWith(PREFIX)) return stored; // plaintext (dev)
        if (KEY == null) {
            throw new IllegalStateException("Stored secret is encrypted but PLAID_TOKEN_KEY is not set");
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(in, 0, iv, 0, IV_LEN);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = c.doFinal(in, IV_LEN, in.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    private static byte[] loadKey() {
        String k = System.getenv("PLAID_TOKEN_KEY");
        if (k == null || k.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(k.trim());
            if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignore) {
            // not base64 — fall through to hashing the raw string
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(k.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }
}
