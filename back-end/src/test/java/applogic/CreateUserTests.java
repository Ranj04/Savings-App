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

public class CreateUserTests {

  @Test(singleThreaded = true)
  public void createUserTest() {
    FindIterable findIterable = Mockito.mock(FindIterable.class);

    MongoCollection mockCollection = Mockito.mock(MongoCollection.class);
    MongoCollection mockAuthCollection = Mockito.mock(MongoCollection.class);

    UserDao userDao = UserDao.getInstance(mockCollection);
    AuthDao authDao = AuthDao.getInstance(mockAuthCollection);
    List<Document> returnList = new ArrayList<>();
    Mockito.doReturn(returnList).when(findIterable).into(Mockito.any());

    Mockito.doReturn(findIterable).when(mockCollection).find((Bson) Mockito.any());

    ArgumentCaptor<Document> argument = ArgumentCaptor.forClass(Document.class);

    ParsedRequest parsedRequest = new ParsedRequest();
    parsedRequest.setPath("/createUser");
    var user = new UserDto();
    user.setPassword(String.valueOf(Math.random()));
    user.setUserName(String.valueOf(Math.random()));
    parsedRequest.setBody(GsonTool.GSON.toJson(user));
    var handler = HandlerFactory.getHandler(parsedRequest);
    var builder = handler.handleRequest(parsedRequest);
    var res = builder.build();
    Assert.assertEquals(res.status, "201 Created");
    Mockito.verify(mockCollection).find(argument.capture());
    Assert.assertEquals(argument.getAllValues().size(), 1);
    Assert.assertEquals(argument.getAllValues().get(0).get("userName"), user.getUserName());

    ArgumentCaptor<Document> findCaptor = ArgumentCaptor.forClass(Document.class);
    Mockito.verify(mockCollection).insertOne(findCaptor.capture());
    Assert.assertEquals(findCaptor.getAllValues().size(), 1);
    Assert.assertEquals(findCaptor.getAllValues().get(0).get("userName"), user.getUserName());
    // Password must be stored as a salted hash, never plaintext or a bare SHA-256.
    String storedHash = findCaptor.getAllValues().get(0).getString("password");
    Assert.assertNotEquals(storedHash, user.getPassword());
    Assert.assertTrue(PasswordUtil.verify(user.getPassword(), storedHash));
    Assert.assertFalse(PasswordUtil.isLegacyHash(storedHash));
    // Account creation auto-logs-in, so it issues a session cookie.
    Assert.assertTrue(res.headers.containsKey("Set-Cookie"));
  }

}
