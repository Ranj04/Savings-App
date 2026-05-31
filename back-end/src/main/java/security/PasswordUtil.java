package security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * Salted, slow password hashing using PBKDF2-HMAC-SHA256 (JDK built-in, no extra
 * dependency). Stored format: {@code pbkdf2$<iterations>$<saltB64>$<hashB64>}.
 *
 * <p>Legacy unsalted SHA-256 hashes (64 hex chars) are still accepted by
 * {@link #verify} so that pre-existing accounts can log in; callers may use
 * {@link #isLegacyHash} to transparently upgrade them on next login.
 */
public final class PasswordUtil {

    private static final SecureRandom RNG = new SecureRandom();
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;

    private PasswordUtil() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        RNG.nextBytes(salt);
        byte[] dk = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
        Base64.Encoder enc = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + enc.encodeToString(salt) + "$" + enc.encodeToString(dk);
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        if (isLegacyHash(stored)) {
            return constantTimeEquals(
                    stored.toLowerCase().getBytes(),
                    DigestUtils.sha256Hex(password).getBytes());
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            Base64.Decoder dec = Base64.getDecoder();
            byte[] salt = dec.decode(parts[2]);
            byte[] expected = dec.decode(parts[3]);
            byte[] actual = pbkdf2(password.toCharArray(), salt, iterations, expected.length * 8);
            return constantTimeEquals(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True if the stored value is an old unsalted SHA-256 hex digest. */
    public static boolean isLegacyHash(String stored) {
        return stored != null && stored.matches("(?i)^[0-9a-f]{64}$");
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash password", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}
