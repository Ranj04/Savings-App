package service;

import dao.AccountDao;
import dao.GoalDao;
import dao.TransactionDao;
import dto.AccountDto;
import dto.GoalDto;
import dto.TransactionDto;
import dto.TransactionType;
import org.bson.types.ObjectId;

/**
 * Single source of truth for moving money in the partition (savings-envelope) model.
 *
 * <p>An account holds a {@code balance} (the real money) and {@code sumAllocated}
 * (money earmarked into goals). Usable = balance - sumAllocated. "Setting money aside"
 * raises sumAllocated and a goal's allocatedAmount in lock-step; releasing reverses it.
 *
 * <p>Every operation uses the DAOs' atomic conditional updates so concurrent requests
 * can never overdraw usable funds or a goal's allocation. The two-document operations
 * (account + goal) compensate on partial failure, keeping the invariant
 * {@code sumAllocated == sum(goal.allocatedAmount)} intact.
 */
public class MoneyService {

    /** Thrown for any business-rule violation; carries a suggested HTTP status. */
    public static class MoneyException extends RuntimeException {
        public final int status;
        public MoneyException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private final AccountDao accountDao;
    private final GoalDao goalDao;
    private final TransactionDao txnDao;

    public MoneyService() {
        this(AccountDao.getInstance(), GoalDao.getInstance(), TransactionDao.getInstance());
    }

    public MoneyService(AccountDao accountDao, GoalDao goalDao, TransactionDao txnDao) {
        this.accountDao = accountDao;
        this.goalDao = goalDao;
        this.txnDao = txnDao;
    }

    private static void requirePositive(double amount) {
        if (!(amount > 0)) {
            throw new MoneyException(400, "Amount must be greater than 0");
        }
    }

    private GoalDto requireGoalInAccount(ObjectId goalId, ObjectId accountId, String user) {
        GoalDto goal = goalDao.byIdForUser(goalId, user);
        if (goal == null) {
            throw new MoneyException(404, "Goal not found");
        }
        if (goal.accountId == null || !goal.accountId.equals(accountId)) {
            throw new MoneyException(400, "Goal does not belong to this account");
        }
        return goal;
    }

    /** Set aside usable money from an account into one of its goals. */
    public TransactionDto allocate(String user, ObjectId accountId, ObjectId goalId, double amount) {
        requirePositive(amount);
        AccountDto acc = accountDao.findByIdForUser(accountId, user);
        if (acc == null) {
            throw new MoneyException(404, "Account not found");
        }
        requireGoalInAccount(goalId, accountId, user);

        if (!accountDao.tryReserve(accountId, user, amount)) {
            throw new MoneyException(400,
                    String.format("Not enough usable funds. Available: %.2f", acc.usable()));
        }
        try {
            goalDao.incAllocated(goalId, user, amount);
        } catch (RuntimeException e) {
            accountDao.releaseReservation(accountId, user, amount); // compensate
            throw e;
        }
        return logTxn(user, TransactionType.Deposit, amount,
                accountId.toHexString(), goalId.toHexString());
    }

    /** Release money from a goal back into the account's usable pool. */
    public TransactionDto release(String user, ObjectId accountId, ObjectId goalId, double amount) {
        requirePositive(amount);
        if (accountDao.findByIdForUser(accountId, user) == null) {
            throw new MoneyException(404, "Account not found");
        }
        requireGoalInAccount(goalId, accountId, user);

        if (!goalDao.tryDecAllocated(goalId, user, amount)) {
            throw new MoneyException(400, "Goal does not have that much set aside");
        }
        accountDao.releaseReservation(accountId, user, amount);
        return logTxn(user, TransactionType.Withdraw, amount,
                accountId.toHexString(), goalId.toHexString());
    }

    /** Move money between two goals in the same account (account totals unchanged). */
    public TransactionDto transferBetweenGoals(String user, ObjectId accountId,
                                               ObjectId fromGoalId, ObjectId toGoalId, double amount) {
        requirePositive(amount);
        if (fromGoalId.equals(toGoalId)) {
            throw new MoneyException(400, "Cannot transfer to the same goal");
        }
        requireGoalInAccount(fromGoalId, accountId, user);
        requireGoalInAccount(toGoalId, accountId, user);

        if (!goalDao.tryDecAllocated(fromGoalId, user, amount)) {
            throw new MoneyException(400, "Source goal does not have that much set aside");
        }
        try {
            goalDao.incAllocated(toGoalId, user, amount);
        } catch (RuntimeException e) {
            goalDao.incAllocated(fromGoalId, user, amount); // compensate
            throw e;
        }
        TransactionDto t = new TransactionDto();
        t.setUserId(user);
        t.setTransactionType(TransactionType.Transfer);
        t.setAmount(amount);
        t.setAccountId(accountId.toHexString());
        t.setFromGoalId(fromGoalId.toHexString());
        t.setToGoalId(toGoalId.toHexString());
        t.setTimestampNow();
        txnDao.put(t);
        return t;
    }

    private TransactionDto logTxn(String user, TransactionType type, double amount,
                                  String accountId, String goalId) {
        TransactionDto t = new TransactionDto();
        t.setUserId(user);
        t.setTransactionType(type);
        t.setAmount(amount);
        t.setAccountId(accountId);
        t.setGoalId(goalId);
        t.setTimestampNow();
        txnDao.put(t);
        return t;
    }
}
