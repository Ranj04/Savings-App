package handler;

import dao.AuthDao;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;

public class LogoutHandler implements BaseHandler {
    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        // Revoke the session server-side so the token can never be replayed,
        // then clear the cookie on the client.
        String token = request.getCookieValue("auth");
        if (token != null && !token.isBlank()) {
            AuthDao.getInstance().deleteByHash(token);
        }
        return new HttpResponseBuilder()
                .setStatus(StatusCodes.OK)
                .setHeader("Set-Cookie", CookieUtil.clearedAuthCookie())
                .setBody(new RestApiAppResponse<>("Logged out"));
    }
}
