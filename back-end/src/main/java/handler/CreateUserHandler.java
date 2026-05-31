package handler;

import dao.AuthDao;
import dao.UserDao;
import dto.AuthDto;
import dto.UserDto;
import org.bson.Document;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;
import security.PasswordUtil;
import security.TokenUtil;

import java.time.Instant;

public class CreateUserHandler implements BaseHandler {

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        UserDto userDto = GsonTool.GSON.fromJson(request.getBody(), dto.UserDto.class);
        if (userDto == null || userDto.getUserName() == null || userDto.getPassword() == null) {
            return new HttpResponseBuilder().setStatus("400 Bad Request")
                    .setBody(new RestApiAppResponse<>(false, "Missing username or password"));
        }
        UserDao userDao = UserDao.getInstance();
        var query = new Document("userName", userDto.getUserName());
        var resultQ = userDao.query(query);
        if (!resultQ.isEmpty()) {
            return new HttpResponseBuilder().setStatus("409 Conflict")
                    .setBody(new RestApiAppResponse<>(false, "Username already taken"));
        }
        userDto.setPassword(PasswordUtil.hash(userDto.getPassword()));
        userDao.put(userDto);

        // Auto-login: create auth token
        AuthDao authDao = AuthDao.getInstance();
        AuthDto authDto = new AuthDto();
        authDto.setUserName(userDto.getUserName());
        long nowSec = Instant.now().getEpochSecond();
        long ttlSec = 86_400L; // 24h
        authDto.setExpireTime(nowSec + ttlSec);
        String token = TokenUtil.newToken(); // opaque, unguessable session id
        authDto.setHash(token);
        authDao.put(authDto);

        return new HttpResponseBuilder()
                .setStatus("201 Created")
                .setHeader("Set-Cookie", CookieUtil.authCookie(token))
                .setHeader("Content-Type", "application/json")
                .setBody(new RestApiAppResponse<>(true, "User created and logged in"));
    }
}
