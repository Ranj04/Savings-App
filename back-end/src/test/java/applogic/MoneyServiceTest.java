package applogic;

import dao.AccountDao;
import dao.GoalDao;
import dao.TransactionDao;
import dto.AccountDto;
import dto.GoalDto;
import org.bson.types.ObjectId;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;
import service.MoneyService;

public class MoneyServiceTest {

    private GoalDto goalIn(ObjectId accId) {
        GoalDto g = new GoalDto();
        g.accountId = accId;
        g.userName = "u";
        g.allocatedAmount = 0.0;
        return g;
    }

    @Test
    public void allocateReservesAndLogsOnSuccess() {
        AccountDao acc = Mockito.mock(AccountDao.class);
        GoalDao goals = Mockito.mock(GoalDao.class);
        TransactionDao txn = Mockito.mock(TransactionDao.class);
        ObjectId accId = new ObjectId();
        ObjectId goalId = new ObjectId();

        AccountDto a = new AccountDto();
        a.userName = "u";
        a.balance = 2000.0;
        a.sumAllocated = 0.0;
        Mockito.when(acc.findByIdForUser(accId, "u")).thenReturn(a);
        Mockito.when(goals.byIdForUser(goalId, "u")).thenReturn(goalIn(accId));
        Mockito.when(acc.tryReserve(accId, "u", 100.0)).thenReturn(true);

        new MoneyService(acc, goals, txn).allocate("u", accId, goalId, 100.0);

        Mockito.verify(acc).tryReserve(accId, "u", 100.0);
        Mockito.verify(goals).incAllocated(goalId, "u", 100.0);
        Mockito.verify(txn).put(Mockito.any());
    }

    @Test
    public void allocateThrowsAndDoesNotLogWhenNoUsableFunds() {
        AccountDao acc = Mockito.mock(AccountDao.class);
        GoalDao goals = Mockito.mock(GoalDao.class);
        TransactionDao txn = Mockito.mock(TransactionDao.class);
        ObjectId accId = new ObjectId();
        ObjectId goalId = new ObjectId();

        AccountDto a = new AccountDto();
        a.userName = "u";
        a.balance = 100.0;
        a.sumAllocated = 100.0; // usable == 0
        Mockito.when(acc.findByIdForUser(accId, "u")).thenReturn(a);
        Mockito.when(goals.byIdForUser(goalId, "u")).thenReturn(goalIn(accId));
        Mockito.when(acc.tryReserve(accId, "u", 50.0)).thenReturn(false);

        MoneyService svc = new MoneyService(acc, goals, txn);
        try {
            svc.allocate("u", accId, goalId, 50.0);
            Assert.fail("expected MoneyException");
        } catch (MoneyService.MoneyException e) {
            Assert.assertEquals(e.status, 400);
        }
        Mockito.verify(goals, Mockito.never()).incAllocated(Mockito.any(), Mockito.any(), Mockito.anyDouble());
        Mockito.verify(txn, Mockito.never()).put(Mockito.any());
    }

    @Test
    public void releaseRejectsWhenGoalLacksAllocation() {
        AccountDao acc = Mockito.mock(AccountDao.class);
        GoalDao goals = Mockito.mock(GoalDao.class);
        TransactionDao txn = Mockito.mock(TransactionDao.class);
        ObjectId accId = new ObjectId();
        ObjectId goalId = new ObjectId();

        AccountDto a = new AccountDto();
        a.userName = "u";
        a.balance = 2000.0;
        a.sumAllocated = 0.0;
        Mockito.when(acc.findByIdForUser(accId, "u")).thenReturn(a);
        Mockito.when(goals.byIdForUser(goalId, "u")).thenReturn(goalIn(accId));
        Mockito.when(goals.tryDecAllocated(goalId, "u", 50.0)).thenReturn(false);

        MoneyService svc = new MoneyService(acc, goals, txn);
        try {
            svc.release("u", accId, goalId, 50.0);
            Assert.fail("expected MoneyException");
        } catch (MoneyService.MoneyException e) {
            Assert.assertEquals(e.status, 400);
        }
        Mockito.verify(acc, Mockito.never()).releaseReservation(Mockito.any(), Mockito.any(), Mockito.anyDouble());
    }
}
