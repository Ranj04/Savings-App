package handler.accounts;

import dao.AccountDao;
import dao.GoalDao;
import dto.AccountDto;
import dto.GoalDto;
import handler.AuthFilter;
import handler.BaseHandler;
import handler.StatusCodes;
import org.bson.Document;
import request.ParsedRequest;
import response.HttpResponseBuilder;
import response.RestApiAppResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ListAccountsWithAllocationsHandler implements BaseHandler {
    @Override
    public HttpResponseBuilder handleRequest(ParsedRequest request) {
        var auth = AuthFilter.doFilter(request);
        if (!auth.isLoggedIn) return new HttpResponseBuilder().setStatus(StatusCodes.UNAUTHORIZED);

        List<AccountDto> accounts = AccountDao.getInstance().query(new Document("userName", auth.userName));
        List<GoalDto> goals = GoalDao.getInstance().query(new Document("userName", auth.userName));

        List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        boolean allocationsSuccess = true;
        try {
            Map<String, List<GoalDto>> byAccount = goals.stream()
                    .collect(Collectors.groupingBy(g -> g.accountId == null ? "null" : g.accountId.toHexString()));

            for (AccountDto acc : accounts) {
                String accId = acc.getUniqueId();
                double balance = acc.balance == null ? 0.0 : acc.balance;

                List<GoalDto> gs = byAccount.getOrDefault(accId, List.of());
                double sumAllocated = gs.stream().mapToDouble(g -> g.allocatedAmount == null ? 0.0 : g.allocatedAmount).sum();
                double unallocated = Math.max(0.0, balance - sumAllocated);

                // Self-heal: keep the denormalized sumAllocated (used by atomic reservations)
                // in sync with the authoritative sum computed from the goals.
                if (acc.sumAllocated == null || Math.abs(acc.sumAllocated - sumAllocated) > 1e-9) {
                    try {
                        AccountDao.getInstance().reconcileSumAllocated(
                                new org.bson.types.ObjectId(accId), auth.userName, sumAllocated);
                    } catch (RuntimeException ignore) {
                        // best-effort; display still correct from computed value
                    }
                }

                java.util.Map<String, Object> accJson = new java.util.HashMap<>();
                accJson.put("_id", accId);
                accJson.put("name", acc.name);
                accJson.put("type", acc.type);
                accJson.put("source", acc.source);
                accJson.put("institutionName", acc.institutionName);
                accJson.put("mask", acc.mask);
                accJson.put("balance", balance);
                accJson.put("sumAllocated", sumAllocated);
                accJson.put("unallocated", unallocated);
                accJson.put("usable", unallocated); // clearer alias for the frontend

                List<java.util.Map<String, Object>> allocs = new java.util.ArrayList<>();
                for (GoalDto g : gs) {
                    java.util.Map<String, Object> go = new java.util.HashMap<>();
                    go.put("goalId", g.id == null ? null : g.id.toHexString());
                    go.put("goalName", g.name);
                    double amt = g.allocatedAmount == null ? 0.0 : g.allocatedAmount;
                    go.put("allocatedAmount", amt);
                    go.put("amount", amt);           // legacy field retained
                    go.put("pct", balance > 0 ? (amt / balance) : 0.0);
                    allocs.add(go);
                }
                accJson.put("allocations", allocs);
                accJson.put("unallocatedPct", balance > 0 ? (unallocated / balance) : 0.0);

                out.add(accJson);
            }
        } catch (Exception e) {
            allocationsSuccess = false;
            // fallback: just return accounts list, no allocations
            out.clear();
            for (AccountDto acc : accounts) {
                java.util.Map<String, Object> accJson = new java.util.HashMap<>();
                accJson.put("_id", acc.getUniqueId());
                accJson.put("name", acc.name);
                accJson.put("type", acc.type);
                accJson.put("balance", acc.balance == null ? 0.0 : acc.balance);
                accJson.put("allocations", false);
                out.add(accJson);
            }
        }
        return new HttpResponseBuilder().setStatus(StatusCodes.OK)
                .setBody(new RestApiAppResponse<>(true, out, allocationsSuccess ? null : "allocations unavailable"));
    }
}
