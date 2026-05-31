package handler.routines;

import com.google.gson.JsonObject;
import dao.RoutineDao;
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
 * Manually apply a routine right now (the "do it manually" option).
 * Body: { routineId }
 */
public class RunRoutineHandler implements BaseHandler {

    private final RoutineScheduler scheduler = new RoutineScheduler();

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
            RoutineDto r = RoutineDao.getInstance().findByIdForUser(new ObjectId(routineId), auth.userName);
            if (r == null) {
                return new HttpResponseBuilder().setStatus(StatusCodes.NOT_FOUND)
                        .setBody(new RestApiAppResponse<>(false, null, "Routine not found"));
            }
            String status = scheduler.apply(r, System.currentTimeMillis());
            boolean ok = "ok".equals(status);
            return new HttpResponseBuilder().setStatus(ok ? StatusCodes.OK : StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(ok, r, ok ? "Routine applied" : status));
        } catch (IllegalArgumentException e) {
            return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
                    .setBody(new RestApiAppResponse<>(false, null, "Invalid routineId"));
        }
    }
}
