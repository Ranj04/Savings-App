package handler;

/**
 * Builds the {@code Set-Cookie} value for the auth session cookie.
 *
 * <p>Uses {@code SameSite=Lax} (never {@code None}) so the browser will not
 * attach the session cookie to cross-site requests — the primary defense
 * against CSRF for a cookie-based session. {@code Secure} is added in
 * production so the cookie is only ever sent over HTTPS.
 */
public final class CookieUtil {

    private CookieUtil() {
    }

    public static String authCookie(String token) {
        return "auth=" + token + "; " + flags();
    }

    public static String clearedAuthCookie() {
        return "auth=; Max-Age=0; " + flags();
    }

    private static String flags() {
        boolean isProd = "production".equalsIgnoreCase(System.getenv("APP_ENV"));
        return isProd
                ? "Path=/; HttpOnly; SameSite=Lax; Secure"
                : "Path=/; HttpOnly; SameSite=Lax";
    }
}
