package handler.goals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.AccountDao;
import dao.GoalDao;
import dto.AccountDto;
import dto.GoalDto;
import handler.AuthFilter;
import handler.BaseHandler;
import handler.StatusCodes;
import org.bson.types.ObjectId;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;

/**
 * Create a goal under a savings account.
 * Request: { "accountName": "...", "goalName": "..." }
 */
public class CreateGoalHandler implements BaseHandler {

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        try {
            AuthFilter.AuthResult auth = AuthFilter.doFilter(request);
            if (!auth.isLoggedIn) {
                return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED)
                        .setBody(new RestApiAppResponse<>(false, "unauthorized"));
            }
            JsonObject body = JsonParser.parseString(request.getBody()).getAsJsonObject();
            String accountName = body.has("accountName") ? body.get("accountName").getAsString() : null;
            String goalName = body.has("goalName") ? body.get("goalName").getAsString() : null;
            if (accountName == null || accountName.isBlank() || goalName == null || goalName.isBlank()) {
                return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                        .setBody(new RestApiAppResponse<>(false, "missing accountName/goalName"));
            }
            // Optional savings target. Reject negatives but treat 0/blank/absent as "no target".
            Double targetAmount = null;
            if (body.has("targetAmount") && !body.get("targetAmount").isJsonNull()) {
                try {
                    double t = body.get("targetAmount").getAsDouble();
                    if (t < 0) {
                        return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                                .setBody(new RestApiAppResponse<>(false, "targetAmount must be >= 0"));
                    }
                    if (t > 0) targetAmount = t;
                } catch (NumberFormatException ex) {
                    return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                            .setBody(new RestApiAppResponse<>(false, "invalid targetAmount"));
                }
            }
            AccountDto account = AccountDao.getInstance().findByNameForUser(accountName, auth.userName);
            if (account == null) {
                return new HttpResponseBuilder().setStatus(StatusCodes.NOT_FOUND)
                        .setBody(new RestApiAppResponse<>(false, "account not found"));
            }
            GoalDto g = new GoalDto();
            g.userName = auth.userName;
            g.accountId = new ObjectId(account.getUniqueId());
            g.name = goalName;
            g.type = "savings";
            g.targetAmount = targetAmount;
            g.createdAt = System.currentTimeMillis();
            try {
                GoalDao.getInstance().put(g);
                JsonObject data = new JsonObject();
                data.addProperty("accountName", accountName);
                data.addProperty("goalName", goalName);
                return new HttpResponseBuilder().setStatus(StatusCodes.CREATED)
                        .setBody(new RestApiAppResponse<>(true, data, null));
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate key")) {
                    JsonObject data = new JsonObject();
                    data.addProperty("duplicate", true);
                    return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                            .setBody(new RestApiAppResponse<>(true, data, null));
                }
                e.printStackTrace();
                return new HttpResponseBuilder().setStatus(StatusCodes.INTERNAL_SERVER_ERROR)
                        .setBody(new RestApiAppResponse<>(false, "internal error"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new HttpResponseBuilder().setStatus(StatusCodes.INTERNAL_SERVER_ERROR)
                    .setBody(new RestApiAppResponse<>(false, "internal error"));
        }
    }
}
