package plaid;

import dao.AccountDao;
import dto.AccountDto;
import org.bson.types.ObjectId;

import java.util.List;

/** Upserts Plaid-reported balances into our AccountDto rows (source = "plaid"). */
public final class PlaidAccountSync {

    private PlaidAccountSync() {
    }

    public static void sync(String userName, String institutionName, List<PlaidClient.PlaidAccount> accounts) {
        AccountDao dao = AccountDao.getInstance();
        long now = System.currentTimeMillis();
        for (PlaidClient.PlaidAccount pa : accounts) {
            AccountDto existing = dao.findByPlaidAccountId(pa.accountId, userName);
            if (existing != null) {
                // Balance is owned by the bank; keep our sumAllocated/goals intact.
                dao.setBalance(new ObjectId(existing.getUniqueId()), userName, pa.current);
            } else {
                AccountDto a = new AccountDto();
                a.userName = userName;
                a.name = pa.name;
                a.type = pa.subtype != null ? pa.subtype : (pa.type != null ? pa.type : "checking");
                a.balance = pa.current;
                a.sumAllocated = 0.0;
                a.createdAt = now;
                a.active = true;
                a.source = "plaid";
                a.plaidAccountId = pa.accountId;
                a.institutionName = institutionName;
                a.mask = pa.mask;
                a.version = 0L;
                dao.put(a);
            }
        }
    }
}
