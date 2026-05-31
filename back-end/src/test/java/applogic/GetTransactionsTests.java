package applogic;

import dto.TransactionDto;
import handler.HandlerFactory;
import handler.StatusCodes;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;
import request.ParsedRequest;

public class GetTransactionsTests {

  @Test(singleThreaded = true)
  public void getTransactions() {
    var tools = new CollectionTestTools();
    var auth = tools.createLogin();

    ParsedRequest parsedRequest = new ParsedRequest();
    parsedRequest.setPath("/getTransactions");
    parsedRequest.setCookieValue("auth", String.valueOf(Math.random()));

    List<Document> returnList = new ArrayList<>();
    returnList.add(new Document("transactionType", "Deposit"));
    Mockito.doReturn(returnList).when(tools.transactionfindIterable).into(Mockito.any());

    var handler = HandlerFactory.getHandler(parsedRequest);
    var builder = handler.handleRequest(parsedRequest);
    var res = builder.build();
    Assert.assertEquals(res.status, StatusCodes.OK);
    Assert.assertEquals(((java.util.List<?>) builder.getBody().data).size(), 1);
  }

}
