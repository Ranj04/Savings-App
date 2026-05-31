package handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dao.TransactionDao;
import dto.TransactionDto;
import org.bson.Document;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;

import java.util.Comparator;
import java.util.List;

public class TransactionsHandler implements BaseHandler {

    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        try {
            AuthFilter.AuthResult auth = AuthFilter.doFilter(request);
            if (!auth.isLoggedIn) {
                return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED)
                        .setBody(new RestApiAppResponse<>(false, "unauthorized"));
            }
            int limit = 5;
            try {
                String q = request.getQueryParam("limit");
                if (q != null && !q.isBlank()) {
                    int L = Integer.parseInt(q);
                    if (L >= 1 && L <= 100) limit = L;
                }
            } catch (Exception ignored) {}

            List<TransactionDto> raw = TransactionDao.getInstance().query(new Document("userId", auth.userName));
            raw = raw.stream()
                    .sorted(Comparator.comparing(
                            TransactionDto::getTimestamp,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            JsonArray out = new JsonArray();
            for (TransactionDto t : raw.stream().limit(limit).toList()) {
                JsonObject row = new JsonObject();
                row.addProperty("_id", t.getUniqueId());
                row.addProperty("userName", auth.userName);
                row.addProperty("type", t.getTransactionType() == null ? null : t.getTransactionType().name().toLowerCase());
                row.addProperty("amount", t.getAmount());
                row.addProperty("timestamp", t.getTimestamp());
                out.add(row);
            }
            return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                    .setBody(new RestApiAppResponse<>(true, out, null));
        } catch (Exception e) {
            e.printStackTrace();
            return new HttpResponseBuilder().setStatus(StatusCodes.INTERNAL_SERVER_ERROR)
                    .setBody(new RestApiAppResponse<>(false, "internal error"));
        }
    }
}
