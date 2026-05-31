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
 * Contribute (set aside) usable money into a savings goal.
 * Body: { goalId, amount }  — the account is inferred from the goal.
 */
public class ContributeGoalHandler implements BaseHandler {

    private final MoneyService money = new MoneyService();

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest req) {
        var auth = AuthFilter.doFilter(req);
        if (!auth.isLoggedIn) return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);

        String goalIdStr = null;
        Double amountVal = null;
        try {
            JsonObject root = JsonParser.parseString(req.getBody()).getAsJsonObject();
            goalIdStr = IdUtil.asId(root.get("goalId"));
            if (root.has("amount")) amountVal = root.get("amount").getAsDouble();
        } catch (Exception ignore) {
            // fall through to validation below
        }

        if (goalIdStr == null || goalIdStr.isBlank() || amountVal == null || amountVal <= 0) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "Invalid goalId or amount"));
        }

        ObjectId goalId;
        try {
            goalId = new ObjectId(goalIdStr);
        } catch (IllegalArgumentException ex) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "Invalid goalId format"));
        }

        GoalDto goal = GoalDao.getInstance().byIdForUser(goalId, auth.userName);
        if (goal == null) {
            return new HttpResponseBuilder().setStatus(StatusCodes.NOT_FOUND)
                    .setBody(new RestApiAppResponse<>(false, null, "Goal not found"));
        }
        if (goal.type != null && !"savings".equalsIgnoreCase(goal.type)) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "Only savings goals accept contributions"));
        }
        if (goal.accountId == null) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "Goal is not linked to an account"));
        }

        try {
            var tx = money.allocate(auth.userName, goal.accountId, goalId, amountVal);
            return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                    .setBody(new RestApiAppResponse<>(true, tx, "Contribution added"));
        } catch (MoneyService.MoneyException e) {
            return new HttpResponseBuilder().setStatus(StatusCodes.forCode(e.status))
                    .setBody(new RestApiAppResponse<>(false, null, e.getMessage()));
        }
    }
}
