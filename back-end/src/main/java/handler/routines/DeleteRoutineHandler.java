package handler.routines;

import com.google.gson.JsonObject;
import dao.RoutineDao;
import handler.AuthFilter;
import handler.BaseHandler;
import handler.GsonTool;
import handler.IdUtil;
import handler.StatusCodes;
import org.bson.types.ObjectId;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;

/** Body: { routineId } */
public class DeleteRoutineHandler implements BaseHandler {

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        var auth = AuthFilter.doFilter(request);
        if (!auth.isLoggedIn) return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);

        JsonObject json = GsonTool.GSON.fromJson(request.getBody(), JsonObject.class);
        String routineId = json == null ? null : IdUtil.asId(json.get("routineId"));
        if (routineId == null) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "routineId is required"));
        }
        try {
            boolean deleted = RoutineDao.getInstance().deleteForUser(new ObjectId(routineId), auth.userName);
            if (!deleted) {
                return new HttpResponseBuilder().setStatus(StatusCodes.NOT_FOUND)
                        .setBody(new RestApiAppResponse<>(false, null, "Routine not found"));
            }
            return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                    .setBody(new RestApiAppResponse<>(true, null, "Routine deleted"));
        } catch (IllegalArgumentException e) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "Invalid routineId"));
        }
    }
}
