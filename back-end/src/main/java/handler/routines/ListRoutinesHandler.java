package handler.routines;

import dao.RoutineDao;
import handler.AuthFilter;
import handler.BaseHandler;
import handler.StatusCodes;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;

public class ListRoutinesHandler implements BaseHandler {

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        var auth = AuthFilter.doFilter(request);
        if (!auth.isLoggedIn) return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);

        var routines = RoutineDao.getInstance().listForUser(auth.userName);
        return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                .setBody(new RestApiAppResponse<>(true, routines, null));
    }
}
