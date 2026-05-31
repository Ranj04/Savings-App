package handler.plaid;

import com.google.gson.JsonObject;
import dao.AccountDao;
import dao.PlaidItemDao;
import dto.PlaidItemDto;
import handler.AuthFilter;
import handler.BaseHandler;
import handler.GsonTool;
import handler.StatusCodes;
import org.bson.Document;
import plaid.PlaidAccountSync;
import plaid.PlaidClient;
import plaid.PlaidConfig;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;
import security.CryptoUtil;

import java.util.List;

/**
 * Exchanges a Plaid public_token (from Plaid Link) for a long-lived access_token,
 * stores it encrypted, and imports the bank's accounts/balances.
 * Body: { public_token, institutionName? }
 */
public class ExchangePublicTokenHandler implements BaseHandler {

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        var auth = AuthFilter.doFilter(request);
        if (!auth.isLoggedIn) return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);

        if (!PlaidConfig.isConfigured()) {
            return new HttpResponseBuilder().setStatus("503 Service Unavailable")
                    .setBody(new RestApiAppResponse<>(false, null,
                            "Bank linking is not configured. Set PLAID_CLIENT_ID and PLAID_SECRET on the server."));
        }

        JsonObject json = GsonTool.GSON.fromJson(request.getBody(), JsonObject.class);
        String publicToken = json != null && json.has("public_token") && !json.get("public_token").isJsonNull()
                ? json.get("public_token").getAsString() : null;
        if (publicToken == null || publicToken.isBlank()) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "public_token is required"));
        }
        String institutionName = json.has("institutionName") && !json.get("institutionName").isJsonNull()
                ? json.get("institutionName").getAsString() : null;

        try {
            PlaidClient client = new PlaidClient();
            String accessToken = client.exchangePublicToken(publicToken);

            PlaidItemDto item = new PlaidItemDto();
            item.userName = auth.userName;
            item.accessTokenEnc = CryptoUtil.encrypt(accessToken);
            item.institutionName = institutionName;
            item.createdAt = System.currentTimeMillis();
            PlaidItemDao.getInstance().put(item);

            List<PlaidClient.PlaidAccount> balances = client.getBalances(accessToken);
            PlaidAccountSync.sync(auth.userName, institutionName, balances);

            var accounts = AccountDao.getInstance().query(new Document("userName", auth.userName));
            return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                    .setBody(new RestApiAppResponse<>(true, accounts, "Bank linked"));
        } catch (PlaidClient.PlaidException e) {
            return new HttpResponseBuilder().setStatus(StatusCodes.forCode(e.status >= 500 ? 502 : 400))
                    .setBody(new RestApiAppResponse<>(false, null, e.getMessage()));
        }
    }
}
