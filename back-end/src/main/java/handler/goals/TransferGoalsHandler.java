package handler.goals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.GoalDao;
import dto.GoalDto;
import handler.AuthFilter;
import handler.BaseHandler;
import handler.IdUtil;
import handler.StatusCodes;
import org.bson.types.ObjectId;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;
import service.MoneyService;

/**
 * Move money from one goal to another within the same account.
 * Body: { fromGoalId, toGoalId, amount }
 */
public class TransferGoalsHandler implements BaseHandler {

    private final MoneyService money = new MoneyService();

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest req) {
        var auth = AuthFilter.doFilter(req);
        if (!auth.isLoggedIn) return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);

        String fromGoalId;
        String toGoalId;
        double amount;
        try {
            JsonObject body = JsonParser.parseString(req.getBody()).getAsJsonObject();
            fromGoalId = IdUtil.asId(body.get("fromGoalId"));
            toGoalId = IdUtil.asId(body.get("toGoalId"));
            amount = body.has("amount") ? body.get("amount").getAsDouble() : 0.0;
        } catch (Exception e) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "Invalid request body"));
        }

        if (fromGoalId == null || toGoalId == null || amount <= 0) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "fromGoalId, toGoalId and a positive amount are required"));
        }

        try {
            GoalDto from = GoalDao.getInstance().byIdForUser(new ObjectId(fromGoalId), auth.userName);
            GoalDto to = GoalDao.getInstance().byIdForUser(new ObjectId(toGoalId), auth.userName);
            if (from == null || to == null) {
                return new HttpResponseBuilder().setStatus(StatusCodes.NOT_FOUND)
                        .setBody(new RestApiAppResponse<>(false, null, "Goal not found"));
            }
            if (from.accountId == null || to.accountId == null || !from.accountId.equals(to.accountId)) {
                return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                        .setBody(new RestApiAppResponse<>(false, null,
                                "Goal-to-goal transfers must be within the same account. "
                                        + "Release the money to usable first to move it elsewhere."));
            }
            var tx = money.transferBetweenGoals(auth.userName, from.accountId,
                    new ObjectId(fromGoalId), new ObjectId(toGoalId), amount);
            return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                    .setBody(new RestApiAppResponse<>(true, tx, "Transfer complete"));
        } catch (MoneyService.MoneyException e) {
            return new HttpResponseBuilder().setStatus(StatusCodes.forCode(e.status))
                    .setBody(new RestApiAppResponse<>(false, null, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "Invalid goalId format"));
        }
    }
}
