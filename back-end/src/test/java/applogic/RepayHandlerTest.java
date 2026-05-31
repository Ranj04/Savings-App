package applogic;

import dto.UserDto;
import handler.RepayHandler;
import org.bson.Document;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import request.ParsedRequest;


import java.util.ArrayList;
import java.util.List;

public class RepayHandlerTest {

    @DataProvider(name = "repayCases")
    public Object[][] repayCases() {
        return new Object[][]{
                // startingBalance, startingDebt, expectedMessage, expectedTxnAmount, expectedBalance, expectedDebt, expectedInterest
                {1200.0, 1000.0, "Your Repayment is completed!",                       1200.0,    0.0,    0.0, 1.1},
                {1000.0, 1000.0, "Repayment denied, You don't have enough balance",       0.0, 1000.0, 1000.0, 1.2},
        };
    }

    @Test(singleThreaded = true, dataProvider = "repayCases")
    public void RepayTest(double startingBalance, double startingDebt, String expectedMessage,
                          double expectedTxnAmount, double expectedBalance, double expectedDebt, double expectedInterest) {

        var tools = new CollectionTestTools();
        var auth = tools.createLogin();
        var handler = new RepayHandler();

        ParsedRequest parsedRequest = new ParsedRequest();
        parsedRequest.setPath("/repay");
        parsedRequest.setCookieValue("auth", String.valueOf(Math.random()));

        List<Document> userReturnList = new ArrayList<>();
        UserDto userDto = new UserDto();
        userDto.setBalance(startingBalance);
        userDto.setDebt(startingDebt);
        userDto.setUserName(auth.getUserName());
        userReturnList.add(userDto.toDocument());
        Mockito.doReturn(userReturnList).when(tools.userfindIterable).into(Mockito.any());

        var builder = handler.handleRequest(parsedRequest);
        var res = builder.build();
        Assert.assertEquals(res.status, expectedMessage);

        ArgumentCaptor<Document> transactionCaptor = ArgumentCaptor.forClass(Document.class);

        Mockito.verify(tools.mockTransactionCollection).insertOne(transactionCaptor.capture());
        var allTransactions = transactionCaptor.getAllValues();
        Assert.assertEquals(allTransactions.get(0).get("userId"), userDto.getUserName());
        Assert.assertEquals(allTransactions.get(0).get("amount"), expectedTxnAmount);
        Assert.assertEquals(allTransactions.get(0).get("transactionType"), "Repay");

        ArgumentCaptor<Document> userCaptor = ArgumentCaptor.forClass(Document.class);

        Mockito.verify(tools.mockUserCollection).insertOne(userCaptor.capture());
        var allUsers = userCaptor.getAllValues();
        Assert.assertEquals(allUsers.get(0).get("userName"), userDto.getUserName());
        Assert.assertEquals(allUsers.get(0).get("balance"), expectedBalance);
        Assert.assertEquals(allUsers.get(0).get("debt"), expectedDebt);
        Assert.assertEquals(allUsers.get(0).get("interest"), expectedInterest);
    }
}
