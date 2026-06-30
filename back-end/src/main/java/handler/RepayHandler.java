package handler;

import dao.TransactionDao;
import dao.UserDao;
import dto.TransactionDto;
import dto.TransactionType;
import org.bson.Document;
import request.ParsedRequest;
import response.HttpResponseBuilder;

public class RepayHandler implements BaseHandler{

    public HttpResponseBuilder handleRequest(ParsedRequest request) {

        AuthFilter.AuthResult authResult = AuthFilter.doFilter(request);
        if (!authResult.isLoggedIn) {
            return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);
        }

        var transactionDto = new TransactionDto();
        transactionDto.setTransactionType(TransactionType.Repay);
        TransactionDao transactionDao = TransactionDao.getInstance();
        HttpResponseBuilder res = new HttpResponseBuilder();

        UserDao userDao = UserDao.getInstance();
        var userRes = userDao.query(new Document("userName", authResult.userName));
        if (userRes.isEmpty()) {
            res.setStatus("User not found");
            return res;
        }

        transactionDto.setUserId(userRes.get(0).getUserName());

        if(userRes.get(0).getDebt() >= 1000){
            userRes.get(0).setInterest(1.2);
        }

        if(userRes.get(0).getBalance() < (userRes.get(0).getDebt() * userRes.get(0).getInterest())){
            res.setStatus("Repayment denied, You don't have enough balance");
            System.out.println(res.getStatus());
            transactionDto.setAmount(0.0);
            userDao.put(userRes.get(0));
            transactionDao.put(transactionDto);
            return res;
        }

        transactionDto.setAmount(userRes.get(0).getDebt() * userRes.get(0).getInterest());

        userRes.get(0).setBalance(userRes.get(0).getBalance() -
                (userRes.get(0).getDebt() * userRes.get(0).getInterest()));
        userRes.get(0).setDebt(0.0);
        userRes.get(0).setInterest(1.1);

        res.setStatus("Your Repayment is completed!");
        System.out.println(res.getStatus());

        userDao.put(userRes.get(0));
        transactionDao.put(transactionDto);
        return res;
    }
}
