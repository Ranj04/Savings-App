package applogic;

import dto.TransactionDto;
import dto.UserDto;
import handler.GsonTool;
import handler.StatusCodes;
import handler.TransferHandler;
import org.bson.Document;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;
import request.ParsedRequest;

import java.util.ArrayList;
import java.util.List;

public class TransferHandlerTest {

    @Test(singleThreaded = true)
    public void makeTransfer(){
        var tools = new CollectionTestTools();

        var auth = tools.createLogin();
        var handler = new TransferHandler();

        String recipient = "recipient-" + Math.random();
        double amount = 50.0;

        ParsedRequest parsedRequest = new ParsedRequest();
        parsedRequest.setPath("/transfer");
        parsedRequest.setCookieValue("auth", String.valueOf(Math.random()));
        TransactionDto transaction = new TransactionDto();
        transaction.setAmount(amount);
        transaction.setToId(recipient);
        parsedRequest.setBody(GsonTool.GSON.toJson(transaction));

        // The handler issues two user lookups: first the sender, then the recipient.
        // They must be distinct users, otherwise the "cannot transfer to yourself" guard trips.
        UserDto fromUser = new UserDto();
        fromUser.setUserName(auth.getUserName());
        fromUser.setBalance(200.0);

        UserDto toUser = new UserDto();
        toUser.setUserName(recipient);
        toUser.setBalance(0.0);

        Mockito.doReturn(new ArrayList<>(List.of(fromUser.toDocument())),
                        new ArrayList<>(List.of(toUser.toDocument())))
                .when(tools.userfindIterable).into(Mockito.any());

        ArgumentCaptor<Document> transactionCaptor = ArgumentCaptor.forClass(Document.class);

        var builder = handler.handleRequest(parsedRequest);
        var res = builder.build();
        Assert.assertEquals(res.status, StatusCodes.OK);

        Mockito.verify(tools.mockTransactionCollection).insertOne(transactionCaptor.capture());
        var allTransactions = transactionCaptor.getAllValues();
        Assert.assertEquals(allTransactions.get(0).get("userId"), auth.getUserName());
        Assert.assertEquals(allTransactions.get(0).get("toId"), recipient);
        Assert.assertEquals(allTransactions.get(0).get("amount"), amount);
        Assert.assertEquals(allTransactions.get(0).get("transactionType"), "Transfer");
    }
}
