package handler;

import dao.TransactionDao;
import dao.UserDao;
import dto.TransactionDto;
import dto.TransactionType;
import dto.TransferRequestDto;

import dto.UserDto;
import org.bson.Document;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;

import java.util.List;

public class TransferHandler implements BaseHandler {

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        AuthFilter.AuthResult authResult = AuthFilter.doFilter(request);
        if (!authResult.isLoggedIn) {
            return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);
        }

        UserDao userDao = UserDao.getInstance();

        TransferRequestDto transferRequestDto = GsonTool.GSON.fromJson(request.getBody(),
                TransferRequestDto.class);

        // A missing or non-positive amount must be rejected. Without this check a
        // negative amount would *increase* the sender's balance and drain the
        // recipient's — a fund-theft bug.
        if (transferRequestDto == null || transferRequestDto.toId == null
                || !(transferRequestDto.amount > 0)) {
            return new HttpResponseBuilder().setStatus("400 Bad Request")
                    .setBody(new RestApiAppResponse<>(false, "A positive amount and a recipient are required."));
        }

        List<UserDto> fromList = userDao.query(new Document("userName", authResult.userName));
        if (fromList.isEmpty()) {
            return new HttpResponseBuilder().setStatus("400 Bad Request")
                    .setBody(new RestApiAppResponse<>(false, "Invalid from user."));
        }
        UserDto fromUser = fromList.get(0);

        List<UserDto> toList = userDao.query(new Document("userName", transferRequestDto.toId));
        if (toList.isEmpty()) {
            return new HttpResponseBuilder().setStatus("400 Bad Request")
                    .setBody(new RestApiAppResponse<>(false, "Invalid user to transfer."));
        }
        UserDto toUser = toList.get(0);

        if (fromUser.getUserName().equals(toUser.getUserName())) {
            return new HttpResponseBuilder().setStatus("400 Bad Request")
                    .setBody(new RestApiAppResponse<>(false, "Cannot transfer to yourself."));
        }

        if (fromUser.getBalance() < transferRequestDto.amount) {
            return new HttpResponseBuilder().setStatus("400 Bad Request")
                    .setBody(new RestApiAppResponse<>(false, "Not enough funds."));
        }

        fromUser.setBalance(fromUser.getBalance() - transferRequestDto.amount);
        toUser.setBalance(toUser.getBalance() + transferRequestDto.amount);
        userDao.put(fromUser);
        userDao.put(toUser);

        TransactionDao transactionDao = TransactionDao.getInstance();
        var transaction = new TransactionDto();
        transaction.setTransactionType(TransactionType.Transfer);
        transaction.setAmount(transferRequestDto.amount);
        transaction.setToId(transferRequestDto.toId);
        transaction.setUserId(authResult.userName);
        transactionDao.put(transaction);

        var res = new RestApiAppResponse<>(true, List.of(fromUser, toUser), null);
        return new HttpResponseBuilder().setStatus("200 OK").setBody(res);
    }

}