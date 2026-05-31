package handler.plaid;

import handler.AuthFilter;
import handler.BaseHandler;
import handler.StatusCodes;
import plaid.PlaidClient;
import plaid.PlaidConfig;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;

import java.util.Map;

/** Returns a Plaid Link token the frontend uses to open Plaid Link. */
public class CreateLinkTokenHandler implements BaseHandler {

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        var auth = AuthFilter.doFilter(request);
        if (!auth.isLoggedIn) return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);

        if (!PlaidConfig.isConfigured()) {
            return new HttpResponseBuilder().setStatus("503 Service Unavailable")
                    .setBody(new RestApiAppResponse<>(false, null,
                            "Bank linking is not configured. Set PLAID_CLIENT_ID and PLAID_SECRET on the server."));
        }

        try {
            String token = new PlaidClient().createLinkToken(auth.userName);
            return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                    .setBody(new RestApiAppResponse<>(true,
                            Map.of("link_token", token, "environment", PlaidConfig.environment()), null));
        } catch (PlaidClient.PlaidException e) {
            return new HttpResponseBuilder().setStatus(StatusCodes.forCode(e.status >= 500 ? 502 : 400))
                    .setBody(new RestApiAppResponse<>(false, null, e.getMessage()));
        }
    }
}
