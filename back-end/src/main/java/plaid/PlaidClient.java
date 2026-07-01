package plaid;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Plaid API client built on the JDK HTTP client + Gson (no third-party SDK).
 * Covers the endpoints we need for linking a bank and reading balances.
 */
public class PlaidClient {

    public static class PlaidException extends RuntimeException {
        public final int status;
        public PlaidException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    public static class PlaidAccount {
        public String accountId;
        public String name;
        public String mask;
        public String type;
        public String subtype;
        public double current;
        public double available;
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private JsonObject post(String path, JsonObject payload) {
        payload.addProperty("client_id", PlaidConfig.clientId());
        payload.addProperty("secret", PlaidConfig.secret());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PlaidConfig.baseUrl() + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (resp.statusCode() >= 400) {
                String msg = obj.has("error_message") && !obj.get("error_message").isJsonNull()
                        ? obj.get("error_message").getAsString()
                        : "Plaid error " + resp.statusCode();
                throw new PlaidException(resp.statusCode(), msg);
            }
            return obj;
        } catch (PlaidException e) {
            throw e;
        } catch (Exception e) {
            throw new PlaidException(502, "Plaid request failed: " + e.getMessage());
        }
    }

    public String createLinkToken(String clientUserId) {
        JsonObject p = new JsonObject();
        JsonObject user = new JsonObject();
        user.addProperty("client_user_id", clientUserId);
        p.add("user", user);
        p.addProperty("client_name", "Personal Finance App");
        JsonArray products = new JsonArray();
        products.add("auth");
        p.add("products", products);
        JsonArray countries = new JsonArray();
        countries.add("US");
        p.add("country_codes", countries);
        p.addProperty("language", "en");
        return post("/link/token/create", p).get("link_token").getAsString();
    }

    public String exchangePublicToken(String publicToken) {
        JsonObject p = new JsonObject();
        p.addProperty("public_token", publicToken);
        return post("/item/public_token/exchange", p).get("access_token").getAsString();
    }

    public List<PlaidAccount> getBalances(String accessToken) {
        JsonObject p = new JsonObject();
        p.addProperty("access_token", accessToken);
        JsonObject r = post("/accounts/balance/get", p);
        List<PlaidAccount> out = new ArrayList<>();
        for (JsonElement el : r.getAsJsonArray("accounts")) {
            JsonObject a = el.getAsJsonObject();
            PlaidAccount pa = new PlaidAccount();
            pa.accountId = a.get("account_id").getAsString();
            pa.name = str(a, "name", "Account");
            pa.mask = str(a, "mask", null);
            pa.type = str(a, "type", null);
            pa.subtype = str(a, "subtype", null);
            JsonObject bal = a.getAsJsonObject("balances");
            pa.current = num(bal, "current");
            pa.available = bal.has("available") && !bal.get("available").isJsonNull()
                    ? num(bal, "available") : pa.current;
            out.add(pa);
        }
        return out;
    }

    private static String str(JsonObject o, String k, String dflt) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : dflt;
    }

    private static double num(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0.0;
    }
}
