package handler;

import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;

public class WhoAmIHandler implements BaseHandler {
    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        var auth = AuthFilter.doFilter(request);
        if (!auth.isLoggedIn) {
            // Standardize to { "userName": null } and 401
            java.util.HashMap<String, Object> resp = new java.util.HashMap<>();
            resp.put("userName", null);
            return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED)
                .setBody(new RestApiAppResponse<>(false, resp, "Not logged in"));
        }
        // Standardize to { "userName": "<name>" } and 200
        java.util.HashMap<String, Object> resp = new java.util.HashMap<>();
        resp.put("userName", auth.userName);
        return new HttpResponseBuilder().setStatus(StatusCodes.OK)
            .setBody(new RestApiAppResponse<>(true, resp, null));
    }
}
