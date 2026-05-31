package applogic;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import dao.UserDao;
import dto.AuthDto;
import dto.UserDto;
import handler.GsonTool;
import handler.HandlerFactory;
import handler.StatusCodes;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;
import request.ParsedRequest;

public class UsernameTakenTest {

  @Test(singleThreaded = true)
  public void userTakenTest() {
    FindIterable findIterable = Mockito.mock(FindIterable.class);

    MongoCollection mockCollection = Mockito.mock(MongoCollection.class);

    UserDao userDao = UserDao.getInstance(mockCollection);
    List<Document> returnList = new ArrayList<>();
    String loginHash = DigestUtils.sha256Hex(String.valueOf(Math.random()));
    var authEntry = new AuthDto();
    authEntry.setHash(loginHash);

    returnList.add(authEntry.toDocument());
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
    Assert.assertEquals(res.status, StatusCodes.CONFLICT);
    Mockito.verify(mockCollection).find(argument.capture());
    Assert.assertEquals(argument.getAllValues().size(), 1);
    Assert.assertEquals(argument.getAllValues().get(0).get("userName"), user.getUserName());

    Assert.assertFalse(builder.getBody().status);
    Assert.assertEquals(builder.getBody().message, "Username already taken");
  }
}
