package handler.routines;

import com.google.gson.JsonObject;
import dao.GoalDao;
import dao.RoutineDao;
import dto.GoalDto;
import dto.RoutineDto;
import handler.AuthFilter;
import handler.BaseHandler;
import handler.GsonTool;
import handler.IdUtil;
import handler.StatusCodes;
import org.bson.types.ObjectId;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;
import service.RoutineScheduler;

/**
 * Create a recurring monthly auto-contribution.
 * Body: { name?, accountId, goalId, amount, dayOfMonth? }
 */
public class CreateRoutineHandler implements BaseHandler {

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        var auth = AuthFilter.doFilter(request);
        if (!auth.isLoggedIn) return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);

        JsonObject json = GsonTool.GSON.fromJson(request.getBody(), JsonObject.class);
        if (json == null || !json.has("accountId") || !json.has("goalId") || !json.has("amount")) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "accountId, goalId and amount are required"));
        }
        String accountId = IdUtil.asId(json.get("accountId"));
        String goalId = IdUtil.asId(json.get("goalId"));
        double amount = json.get("amount").getAsDouble();
        int dayOfMonth = json.has("dayOfMonth") ? json.get("dayOfMonth").getAsInt() : 1;
        String name = json.has("name") && !json.get("name").isJsonNull() ? json.get("name").getAsString() : null;

        if (accountId == null || goalId == null || amount <= 0) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "A valid accountId, goalId and positive amount are required"));
        }

        ObjectId goalOid;
        try {
            goalOid = new ObjectId(goalId);
            new ObjectId(accountId); // validate format
        } catch (IllegalArgumentException e) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "Invalid accountId or goalId"));
        }

        // Verify the goal belongs to this user and the named account.
        GoalDto goal = GoalDao.getInstance().byIdForUser(goalOid, auth.userName);
        if (goal == null || goal.accountId == null || !goal.accountId.toHexString().equals(accountId)) {
            return new HttpResponseBuilder().setStatus(StatusCodes.NOT_FOUND)
                    .setBody(new RestApiAppResponse<>(false, null, "Goal not found in this account"));
        }

        long now = System.currentTimeMillis();
        RoutineDto r = new RoutineDto();
        r.userName = auth.userName;
        r.name = name != null ? name : goal.name;
        r.accountId = accountId;
        r.goalId = goalId;
        r.amount = amount;
        r.dayOfMonth = Math.max(1, Math.min(28, dayOfMonth));
        r.active = true;
        r.createdAt = now;
        r.nextRunAtMillis = RoutineScheduler.computeNextRun(now, r.dayOfMonth);
        RoutineDao.getInstance().put(r);

        return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                .setBody(new RestApiAppResponse<>(true, r, "Routine created"));
    }
}
