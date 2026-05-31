package applogic;

import com.google.gson.JsonObject;
import dto.AccountDto;
import dto.GoalDto;
import handler.StatusCodes;
import handler.WithdrawHandler;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;
import request.ParsedRequest;

import java.util.ArrayList;
import java.util.List;

public class WithdrawHandlerTest {

    @Test(singleThreaded = true)
    public void makeWithdraw(){
        var tools = new CollectionTestTools();
        var auth = tools.createLogin();
        var handler = new WithdrawHandler();

        ObjectId accId = new ObjectId();
        ObjectId goalId = new ObjectId();
        double amount = 50.0;

        AccountDto account = new AccountDto();
        account.userName = auth.getUserName();
        account.name = "Savings";
        account.type = "savings";
        account.balance = 200.0;
        Document accountDoc = account.toDocument().append("_id", accId);
        Mockito.doReturn(new ArrayList<>(List.of(accountDoc))).when(tools.accountfindIterable).into(Mockito.any());
        Mockito.doReturn(accountDoc).when(tools.accountfindIterable).first();

        // Goal has $100 allocated, so a $50 withdrawal is allowed.
        GoalDto goal = new GoalDto();
        goal.userName = auth.getUserName();
        goal.accountId = accId;
        goal.name = "Goal";
        goal.allocatedAmount = 100.0;
        Document goalDoc = goal.toDocument().append("_id", goalId);
        Mockito.doReturn(new ArrayList<>(List.of(goalDoc))).when(tools.goalfindIterable).into(Mockito.any());
        Mockito.doReturn(goalDoc).when(tools.goalfindIterable).first();

        ParsedRequest parsedRequest = new ParsedRequest();
        parsedRequest.setPath("/withdraw");
        parsedRequest.setCookieValue("auth", String.valueOf(Math.random()));
        JsonObject body = new JsonObject();
        body.addProperty("accountId", accId.toHexString());
        body.addProperty("goalId", goalId.toHexString());
        body.addProperty("amount", amount);
        parsedRequest.setBody(body.toString());

        ArgumentCaptor<Document> transactionCaptor = ArgumentCaptor.forClass(Document.class);

        var builder = handler.handleRequest(parsedRequest);
        var res = builder.build();
        Assert.assertEquals(res.status, StatusCodes.OK);

        Mockito.verify(tools.mockTransactionCollection).insertOne(transactionCaptor.capture());
        var allTransactions = transactionCaptor.getAllValues();
        Assert.assertEquals(allTransactions.get(0).get("userId"), auth.getUserName());
        Assert.assertEquals(allTransactions.get(0).get("amount"), amount);
        Assert.assertEquals(allTransactions.get(0).get("transactionType"), "Withdraw");
    }
}
