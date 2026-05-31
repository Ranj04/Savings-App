package plaid;

/** Reads Plaid credentials/environment from env vars. */
public final class PlaidConfig {

    private PlaidConfig() {
    }

    public static String clientId() {
        return System.getenv("PLAID_CLIENT_ID");
    }

    public static String secret() {
        return System.getenv("PLAID_SECRET");
    }

    public static String environment() {
        return System.getenv().getOrDefault("PLAID_ENV", "sandbox");
    }

    public static boolean isConfigured() {
        return notBlank(clientId()) && notBlank(secret());
    }

    public static String baseUrl() {
        return switch (environment().toLowerCase()) {
            case "production" -> "https://production.plaid.com";
            case "development" -> "https://development.plaid.com";
            default -> "https://sandbox.plaid.com";
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
