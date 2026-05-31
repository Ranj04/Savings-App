package handler;

import com.google.gson.JsonObject;
import org.bson.types.ObjectId;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;
import service.MoneyService;

/**
 * Release money from a goal back into the account's usable pool.
 * Body: { accountId, goalId, amount }
 */
public class WithdrawHandler implements BaseHandler {

    private final MoneyService money = new MoneyService();

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        var auth = AuthFilter.doFilter(request);
        if (!auth.isLoggedIn) return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);

        JsonObject json = GsonTool.GSON.fromJson(request.getBody(), JsonObject.class);
        if (json == null || !json.has("accountId") || !json.has("goalId") || !json.has("amount")) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "accountId, goalId, amount are required"));
        }

        String accountId = IdUtil.asId(json.get("accountId"));
        String goalId = IdUtil.asId(json.get("goalId"));
        double amount = json.get("amount").getAsDouble();
        if (accountId == null || goalId == null) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "Invalid accountId or goalId"));
        }

        try {
            var tx = money.release(auth.userName, new ObjectId(accountId), new ObjectId(goalId), amount);
            return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                    .setBody(new RestApiAppResponse<>(true, tx, null));
        } catch (MoneyService.MoneyException e) {
            return new HttpResponseBuilder().setStatus(StatusCodes.forCode(e.status))
                    .setBody(new RestApiAppResponse<>(false, null, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "Invalid id format"));
        }
    }
}
