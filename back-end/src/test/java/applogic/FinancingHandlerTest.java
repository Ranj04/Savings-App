package applogic;

import dto.TransactionDto;
import dto.UserDto;
import handler.GsonTool;
import handler.FinancingHandler;
import org.bson.Document;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import request.ParsedRequest;

import java.util.ArrayList;
import java.util.List;

public class FinancingHandlerTest {

    @DataProvider(name = "financingCases")
    public Object[][] financingCases() {
        return new Object[][]{
                // startingBalance, startingDebt, requestAmount, messageSuffix, expectedBalance, expectedDebt
                {700.0,  0.0,    1000.0, ", you got financing!",                       1700.0, 1000.0},
                {400.0,  0.0,    1000.0, ", Financing denied, Not enough balance",      400.0,    0.0},
                {5000.0, 9000.0, 1000.0, ", Financing denied, You already have debt",  5000.0, 9000.0},
        };
    }

    @Test(singleThreaded = true, dataProvider = "financingCases")
    public void financingTest(double startingBalance, double startingDebt, double requestAmount,
                              String messageSuffix, double expectedBalance, double expectedDebt) {

        var tools = new CollectionTestTools();
        var auth = tools.createLogin();
        var handler = new FinancingHandler();

        ParsedRequest parsedRequest = new ParsedRequest();
        parsedRequest.setPath("/financing");
        parsedRequest.setCookieValue("auth", String.valueOf(Math.random()));

        TransactionDto transaction = new TransactionDto();
        transaction.setAmount(requestAmount);
        parsedRequest.setBody(GsonTool.GSON.toJson(transaction));

        List<Document> userReturnList = new ArrayList<>();
        UserDto userDto = new UserDto();
        userDto.setBalance(startingBalance);
        userDto.setDebt(startingDebt);
        userDto.setUserName(auth.getUserName());
        userReturnList.add(userDto.toDocument());
        Mockito.doReturn(userReturnList).when(tools.userfindIterable).into(Mockito.any());

        var builder = handler.handleRequest(parsedRequest);
        var res = builder.build();
        Assert.assertEquals(res.status, userDto.getUserName() + messageSuffix);

        ArgumentCaptor<Document> transactionCaptor = ArgumentCaptor.forClass(Document.class);

        Mockito.verify(tools.mockTransactionCollection).insertOne(transactionCaptor.capture());
        var allTransactions = transactionCaptor.getAllValues();
        Assert.assertEquals(allTransactions.get(0).get("userId"), userDto.getUserName());
        Assert.assertEquals(allTransactions.get(0).get("amount"), transaction.getAmount());
        Assert.assertEquals(allTransactions.get(0).get("transactionType"), "Financing");

        ArgumentCaptor<Document> userCaptor = ArgumentCaptor.forClass(Document.class);

        Mockito.verify(tools.mockUserCollection).insertOne(userCaptor.capture());
        var allUsers = userCaptor.getAllValues();
        Assert.assertEquals(allUsers.get(0).get("userName"), userDto.getUserName());
        Assert.assertEquals(allUsers.get(0).get("balance"), expectedBalance);
        Assert.assertEquals(allUsers.get(0).get("debt"), expectedDebt);
    }
}
