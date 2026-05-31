package handler.accounts;

import com.google.gson.JsonObject;
import dao.AccountDao;
import dto.AccountDto;
import handler.AuthFilter;
import handler.BaseHandler;
import handler.GsonTool;
import handler.IdUtil;
import handler.StatusCodes;
import org.bson.types.ObjectId;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;

/**
 * Record income / add funds into a manual account (e.g. a $2,000 paycheck).
 * Raises the account balance, which increases usable (unallocated) money.
 * Body: { accountId, amount }
 *
 * <p>Plaid-linked accounts get their balance from the bank, so this is rejected for them.
 */
public class AddFundsHandler implements BaseHandler {

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        var auth = AuthFilter.doFilter(request);
        if (!auth.isLoggedIn) return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);

        JsonObject json = GsonTool.GSON.fromJson(request.getBody(), JsonObject.class);
        if (json == null || !json.has("accountId") || !json.has("amount")) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "accountId and amount are required"));
        }
        String accountId = IdUtil.asId(json.get("accountId"));
        double amount = json.get("amount").getAsDouble();
        if (accountId == null || amount <= 0) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "A valid accountId and positive amount are required"));
        }

        ObjectId id;
        try {
            id = new ObjectId(accountId);
        } catch (IllegalArgumentException e) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "Invalid accountId"));
        }

        AccountDao dao = AccountDao.getInstance();
        AccountDto acc = dao.findByIdForUser(id, auth.userName);
        if (acc == null) {
            return new HttpResponseBuilder().setStatus(StatusCodes.NOT_FOUND)
                    .setBody(new RestApiAppResponse<>(false, null, "Account not found"));
        }
        if ("plaid".equalsIgnoreCase(acc.source)) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null,
                            "This account is linked to a bank; its balance is synced from Plaid and can't be edited here."));
        }

        dao.creditBalance(id, auth.userName, amount);
        AccountDto updated = dao.findByIdForUser(id, auth.userName);
        return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                .setBody(new RestApiAppResponse<>(true, updated, "Funds added"));
    }
}
