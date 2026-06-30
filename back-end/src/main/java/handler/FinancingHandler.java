package handler;

import dao.TransactionDao;
import dao.UserDao;
import dto.TransactionDto;
import dto.TransactionType;
import org.bson.Document;
import request.ParsedRequest;
import response.HttpResponseBuilder;

public class FinancingHandler implements BaseHandler{

    public HttpResponseBuilder handleRequest(ParsedRequest request) {

        //can not make a loan if a user already has debt
        //upper limit of amount of debt is twice of balance
        //

        AuthFilter.AuthResult authResult = AuthFilter.doFilter(request);
        if (!authResult.isLoggedIn) {
            return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);
        }

        TransactionDto transactionDto = GsonTool.GSON.fromJson(request.getBody(),
                TransactionDto.class);
        transactionDto.setTransactionType(TransactionType.Financing);
        TransactionDao transactionDao = TransactionDao.getInstance();
        HttpResponseBuilder res = new HttpResponseBuilder();

        UserDao userDao = UserDao.getInstance();
        var userRes = userDao.query(new Document("userName", authResult.userName));
        if (userRes.isEmpty()) {
            res.setStatus("User not found");
            return res;
        }

        transactionDto.setUserId(userRes.get(0).getUserName());

        if(userRes.get(0).getBalance() * 2 < transactionDto.getAmount()){
            res.setStatus(userRes.get(0).getUserName() + ", Financing denied, Not enough balance");
            System.out.println(res.getStatus());
            userDao.put(userRes.get(0));
            transactionDao.put(transactionDto);
            return res;
        }

        if(userRes.get(0).getDebt() > 0.0){
            res.setStatus(userRes.get(0).getUserName() + ", Financing denied, You already have debt");
            System.out.println(res.getStatus());
            userDao.put(userRes.get(0));
            transactionDao.put(transactionDto);
            return res;
        }

        userRes.get(0).setBalance(userRes.get(0).getBalance() + transactionDto.getAmount());
        userRes.get(0).setDebt(transactionDto.getAmount());

        res.setStatus(userRes.get(0).getUserName() + ", you got financing!");
        System.out.println(res.getStatus());

        userDao.put(userRes.get(0));
        transactionDao.put(transactionDto);
        return res;
    }
}
