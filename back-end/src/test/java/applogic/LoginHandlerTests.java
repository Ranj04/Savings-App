package applogic;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import dao.AuthDao;
import dao.UserDao;
import dto.UserDto;
import handler.GsonTool;
import handler.HandlerFactory;
import handler.StatusCodes;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;
import request.ParsedRequest;
import security.PasswordUtil;

public class LoginHandlerTests {

  @Test(singleThreaded = true)
  public void loginTest() {
    FindIterable findIterable = Mockito.mock(FindIterable.class);

    MongoCollection mockUserCollection = Mockito.mock(MongoCollection.class);
    MongoCollection mockAuthCollection = Mockito.mock(MongoCollection.class);

    UserDao userDao = UserDao.getInstance(mockUserCollection);
    AuthDao authDao = AuthDao.getInstance(mockAuthCollection);

    // The stored user has a salted PBKDF2 hash of a known password.
    String userName = "alice";
    String password = "correct horse battery staple";
    var storedUser = new UserDto();
    storedUser.setUserName(userName);
    storedUser.setPassword(PasswordUtil.hash(password));

    List<Document> returnList = new ArrayList<>();
    returnList.add(storedUser.toDocument());
    Mockito.doReturn(returnList).when(findIterable).into(Mockito.any());
    Mockito.doReturn(findIterable).when(mockUserCollection).find((Bson) Mockito.any());

    ArgumentCaptor<Document> argument = ArgumentCaptor.forClass(Document.class);

    ParsedRequest parsedRequest = new ParsedRequest();
    parsedRequest.setPath("/login");

    var login = new UserDto();
    login.setUserName(userName);
    login.setPassword(password); // plaintext password the client submits
    parsedRequest.setBody(GsonTool.GSON.toJson(login));

    var handler = HandlerFactory.getHandler(parsedRequest);
    var builder = handler.handleRequest(parsedRequest);
    var res = builder.build();

    Assert.assertEquals(res.status, StatusCodes.OK);

    // The user is looked up by name only — never by a derived password hash.
    Mockito.verify(mockUserCollection).find(argument.capture());
    Assert.assertEquals(argument.getAllValues().size(), 1);
    Assert.assertEquals(argument.getAllValues().get(0).get("userName"), userName);
    Assert.assertNull(argument.getAllValues().get(0).get("password"));
    Assert.assertTrue(res.headers.containsKey("Set-Cookie"));

    // An opaque random session token is persisted and set as the cookie.
    ArgumentCaptor<Document> authCaptor = ArgumentCaptor.forClass(Document.class);
    Mockito.verify(mockAuthCollection).insertOne(authCaptor.capture());
    Assert.assertEquals(authCaptor.getAllValues().get(0).get("userName"), userName);
    String token = authCaptor.getAllValues().get(0).getString("hash");
    Assert.assertNotNull(token);
    Assert.assertTrue(res.headers.get("Set-Cookie").contains(token));
    // The token must not be the old forgeable sha256(userName + expireTime) value.
    Assert.assertNotEquals(token,
        org.apache.commons.codec.digest.DigestUtils.sha256Hex(
            userName + authCaptor.getAllValues().get(0).getLong("expireTime")));
  }

  @Test(singleThreaded = true)
  public void loginRejectsWrongPassword() {
    FindIterable findIterable = Mockito.mock(FindIterable.class);
    MongoCollection mockUserCollection = Mockito.mock(MongoCollection.class);
    MongoCollection mockAuthCollection = Mockito.mock(MongoCollection.class);
    UserDao.getInstance(mockUserCollection);
    AuthDao.getInstance(mockAuthCollection);

    var storedUser = new UserDto();
    storedUser.setUserName("alice");
    storedUser.setPassword(PasswordUtil.hash("the-right-password"));

    List<Document> returnList = new ArrayList<>();
    returnList.add(storedUser.toDocument());
    Mockito.doReturn(returnList).when(findIterable).into(Mockito.any());
    Mockito.doReturn(findIterable).when(mockUserCollection).find((Bson) Mockito.any());

    ParsedRequest parsedRequest = new ParsedRequest();
    parsedRequest.setPath("/login");
    var login = new UserDto();
    login.setUserName("alice");
    login.setPassword("the-wrong-password");
    parsedRequest.setBody(GsonTool.GSON.toJson(login));

    var handler = HandlerFactory.getHandler(parsedRequest);
    var res = handler.handleRequest(parsedRequest).build();

    Assert.assertEquals(res.status, "401 Unauthorized");
    Assert.assertFalse(res.headers.containsKey("Set-Cookie"));
  }
}
