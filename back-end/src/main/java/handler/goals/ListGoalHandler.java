package handler.goals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dao.GoalDao;
import dto.GoalDto;
import handler.AuthFilter;
import handler.BaseHandler;
import handler.StatusCodes;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;

import java.util.List;

public class ListGoalHandler implements BaseHandler {

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        try {
            AuthFilter.AuthResult auth = AuthFilter.doFilter(request);
            if (!auth.isLoggedIn) {
                return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED)
                        .setBody(new RestApiAppResponse<>(false, "unauthorized"));
            }
            List<GoalDto> goals = GoalDao.getInstance().findByUser(auth.userName);
            JsonArray out = new JsonArray();
            if (goals != null) {
                for (GoalDto g : goals) {
                    JsonObject o = new JsonObject();
                    o.addProperty("_id", g.id.toHexString());
                    o.addProperty("userName", g.userName);
                    o.addProperty("accountId", g.accountId == null ? null : g.accountId.toHexString());
                    o.addProperty("name", g.name);
                    o.addProperty("allocatedAmount", g.allocatedAmount == null ? 0.0 : g.allocatedAmount);
                    if (g.targetAmount != null) o.addProperty("targetAmount", g.targetAmount);
                    o.addProperty("createdAt", g.createdAt);
                    out.add(o);
                }
            }
            return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                    .setBody(new RestApiAppResponse<>(true, out, null));
        } catch (Exception e) {
            e.printStackTrace();
            return new HttpResponseBuilder().setStatus(StatusCodes.INTERNAL_SERVER_ERROR)
                    .setBody(new RestApiAppResponse<>(false, "internal error"));
        }
    }
}
