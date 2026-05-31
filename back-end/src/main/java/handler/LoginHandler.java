package handler;

import dao.AuthDao;
import dao.UserDao;
import dto.AuthDto;
import dto.BaseDto;
import dto.UserDto;
import org.bson.Document;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;
import security.PasswordUtil;
import security.TokenUtil;

import java.time.Instant;
import java.util.Collections;

class LoginDto {
    String userName;
    String password;
}

public class LoginHandler implements BaseHandler {

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        LoginDto userDto = GsonTool.GSON.fromJson(request.getBody(), LoginDto.class);
        if (userDto == null || userDto.userName == null || userDto.password == null) {
            var body = new RestApiAppResponse<BaseDto>(false, Collections.emptyList(), "Missing username or password");
            return new HttpResponseBuilder().setStatus("400 Bad Request")
                    .setHeader("Content-Type", "application/json")
                    .setBody(body);
        }
        UserDao userDao = UserDao.getInstance();
        AuthDao authDao = AuthDao.getInstance();

        // Look the user up by name only, then verify the password against the
        // stored salted hash. We must never query by a derived hash, because
        // PBKDF2 uses a per-user random salt.
        var result = userDao.query(new Document("userName", userDto.userName));
        UserDto user = result.isEmpty() ? null : result.get(0);
        if (user == null || !PasswordUtil.verify(userDto.password, user.getPassword())) {
            var body = new RestApiAppResponse<BaseDto>(false, Collections.emptyList(), "Invalid credentials");
            return new HttpResponseBuilder().setStatus("401 Unauthorized")
                    .setHeader("Content-Type", "application/json")
                    .setBody(body);
        }

        // Transparently upgrade legacy unsalted SHA-256 hashes on successful login.
        if (PasswordUtil.isLegacyHash(user.getPassword())) {
            user.setPassword(PasswordUtil.hash(userDto.password));
            userDao.put(user);
        }

        AuthDto authDto = new AuthDto();
        authDto.setUserName(user.getUserName());
        long nowSec = Instant.now().getEpochSecond();
        long ttlSec = 86_400L; // 24h
        authDto.setExpireTime(nowSec + ttlSec);
        String token = TokenUtil.newToken(); // opaque, unguessable session id
        authDto.setHash(token);
        authDao.put(authDto);

        var body = new RestApiAppResponse<BaseDto>(true, Collections.emptyList(), "Login successful");
        return new HttpResponseBuilder()
                .setStatus("200 OK")
                .setHeader("Set-Cookie", CookieUtil.authCookie(token))
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
