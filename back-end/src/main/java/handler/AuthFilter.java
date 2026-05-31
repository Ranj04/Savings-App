package handler;

import dao.AuthDao;
import org.bson.Document;
import request.ParsedRequest;

public class AuthFilter {

    public static class AuthResult {
        public boolean isLoggedIn;
        public String userName;
    }

    public static AuthResult doFilter(ParsedRequest parsedRequest) {
        AuthDao authDao = AuthDao.getInstance();
        var result = new AuthResult();

        String authHash = parsedRequest.getCookieValue("auth");
        if (authHash == null || authHash.isBlank()) {
            result.isLoggedIn = false;
            return result;
        }

        Document filter = new Document("hash", authHash);
        var authRes = authDao.query(filter);
        if (authRes.isEmpty()) {
            result.isLoggedIn = false;
            return result;
        }
        long nowSec = java.time.Instant.now().getEpochSecond();
        Long expireTime = authRes.get(0).getExpireTime();
        if (expireTime == null || expireTime <= nowSec) {
            result.isLoggedIn = false;
            return result;
        }
        result.isLoggedIn = true;
        result.userName = authRes.get(0).getUserName();
        return result;
    }
}
