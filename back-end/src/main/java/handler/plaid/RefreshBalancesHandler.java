package handler.plaid;

import dao.AccountDao;
import dao.PlaidItemDao;
import dto.PlaidItemDto;
import handler.AuthFilter;
import handler.BaseHandler;
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

/** Re-pulls live balances for all of the user's linked banks. */
public class RefreshBalancesHandler implements BaseHandler {

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        var auth = AuthFilter.doFilter(request);
        if (!auth.isLoggedIn) return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);

        if (!PlaidConfig.isConfigured()) {
            return new HttpResponseBuilder().setStatus("503 Service Unavailable")
                    .setBody(new RestApiAppResponse<>(false, null,
                            "Bank linking is not configured."));
        }

        List<PlaidItemDto> items = PlaidItemDao.getInstance().findByUser(auth.userName);
        PlaidClient client = new PlaidClient();
        try {
            for (PlaidItemDto item : items) {
                String accessToken = CryptoUtil.decrypt(item.accessTokenEnc);
                var balances = client.getBalances(accessToken);
                PlaidAccountSync.sync(auth.userName, item.institutionName, balances);
            }
        } catch (PlaidClient.PlaidException e) {
            return new HttpResponseBuilder().setStatus(StatusCodes.forCode(e.status >= 500 ? 502 : 400))
                    .setBody(new RestApiAppResponse<>(false, null, e.getMessage()));
        }

        var accounts = AccountDao.getInstance().query(new Document("userName", auth.userName));
        return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                .setBody(new RestApiAppResponse<>(true, accounts, "Balances refreshed"));
    }
}
