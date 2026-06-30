package handler.accounts;

import com.google.gson.Gson;
import dto.TransactionDto;
import handler.AuthFilter;
import handler.BaseHandler;
import handler.StatusCodes;
import org.bson.types.ObjectId;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;
import service.MoneyService;

/**
 * Move usable money from one savings account to another (optionally re-homing a
 * goal allocation). Delegates to {@link MoneyService} so the move is atomic and
 * keeps each account's {@code sumAllocated == sum(goal.allocatedAmount)} invariant.
 *
 * <p>Body: { fromAccountId, toAccountId, amount, [fromGoalId], [toGoalId] }
 */
public class TransferBetweenAccountsHandler implements BaseHandler {

  static class Body { String fromAccountId; String toAccountId; String fromGoalId; String toGoalId; Double amount; }

  private final MoneyService money = new MoneyService();

  @Override
  public HttpResponseBuilder handleRequest(ParsedRequest req) {
    var auth = AuthFilter.doFilter(req);
    if (!auth.isLoggedIn) return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);

    Body b = new Gson().fromJson(req.getBody(), Body.class);
    if (b == null || b.amount == null || b.amount <= 0 || b.fromAccountId == null || b.toAccountId == null) {
      return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
          .setBody(new RestApiAppResponse<>(false, null, "fromAccountId, toAccountId and positive amount required"));
    }

    ObjectId fromAccountId;
    ObjectId toAccountId;
    ObjectId fromGoalId = null;
    ObjectId toGoalId = null;
    try {
      fromAccountId = new ObjectId(b.fromAccountId);
      toAccountId = new ObjectId(b.toAccountId);
      if (b.fromGoalId != null && !b.fromGoalId.isBlank()) fromGoalId = new ObjectId(b.fromGoalId);
      if (b.toGoalId != null && !b.toGoalId.isBlank()) toGoalId = new ObjectId(b.toGoalId);
    } catch (IllegalArgumentException e) {
      return new HttpResponseBuilder().setStatus(StatusCodes.BAD_REQUEST)
          .setBody(new RestApiAppResponse<>(false, null, "Invalid account or goal id"));
    }

    try {
      TransactionDto tx = money.transferBetweenAccounts(
          auth.userName, fromAccountId, toAccountId, fromGoalId, toGoalId, b.amount);
      return new HttpResponseBuilder().setStatus(StatusCodes.OK)
          .setBody(new RestApiAppResponse<>(true, tx, "Transfer complete"));
    } catch (MoneyService.MoneyException e) {
      return new HttpResponseBuilder().setStatus(StatusCodes.forCode(e.status))
          .setBody(new RestApiAppResponse<>(false, null, e.getMessage()));
    }
  }
}
