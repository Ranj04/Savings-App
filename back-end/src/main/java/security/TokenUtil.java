package security;

import java.security.SecureRandom;

/**
 * Generates opaque, unguessable session tokens from a CSPRNG.
 *
 * <p>Replaces the previous scheme that derived the session token from
 * {@code sha256(userName + expireTime)}, which was forgeable by anyone who knew
 * (or could brute-force) those two values.
 */
public final class TokenUtil {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256 bits of entropy

    private TokenUtil() {
    }

    public static String newToken() {
        byte[] buf = new byte[TOKEN_BYTES];
        RNG.nextBytes(buf);
        StringBuilder sb = new StringBuilder(buf.length * 2);
        for (byte b : buf) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
