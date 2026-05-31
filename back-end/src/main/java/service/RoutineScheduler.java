package service;

import dao.RoutineDao;
import dto.RoutineDto;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Applies recurring monthly "set aside" routines automatically. Every few minutes
 * it finds routines whose next run is due, applies them via {@link MoneyService},
 * and advances them to the next month. If an account lacks usable funds when a
 * routine fires, it is skipped (recorded as a failed run) and retried next cycle —
 * never overdrawing.
 */
public class RoutineScheduler {

    private final RoutineDao routineDao;
    private final MoneyService money;
    private final ScheduledExecutorService exec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "routine-scheduler");
                t.setDaemon(true);
                return t;
            });

    public RoutineScheduler() {
        this(RoutineDao.getInstance(), new MoneyService());
    }

    public RoutineScheduler(RoutineDao routineDao, MoneyService money) {
        this.routineDao = routineDao;
        this.money = money;
    }

    public void start() {
        // Run shortly after boot, then every 5 minutes.
        exec.scheduleAtFixedRate(this::safeTick, 10, 5 * 60, TimeUnit.SECONDS);
    }

    private void safeTick() {
        try {
            tick(System.currentTimeMillis());
        } catch (RuntimeException e) {
            System.err.println("RoutineScheduler tick failed: " + e.getMessage());
        }
    }

    void tick(long nowMillis) {
        List<RoutineDto> due = routineDao.findDue(nowMillis);
        for (RoutineDto r : due) {
            apply(r, nowMillis);
        }
    }

    /** Apply a single routine now, advance its schedule, and record the outcome. */
    public String apply(RoutineDto r, long nowMillis) {
        String status;
        try {
            money.allocate(r.userName, new ObjectId(r.accountId), new ObjectId(r.goalId),
                    r.amount == null ? 0.0 : r.amount);
            status = "ok";
        } catch (MoneyService.MoneyException e) {
            status = "skipped: " + e.getMessage();
        } catch (RuntimeException e) {
            status = "error: " + e.getMessage();
        }
        long next = computeNextRun(nowMillis, r.dayOfMonth == null ? 1 : r.dayOfMonth);
        if (r.getUniqueId() != null) {
            routineDao.recordRun(new ObjectId(r.getUniqueId()), next, nowMillis, status);
        }
        return status;
    }

    /** Next 00:00 UTC on the given day-of-month strictly after {@code fromMillis}. */
    public static long computeNextRun(long fromMillis, int dayOfMonth) {
        int dom = Math.max(1, Math.min(28, dayOfMonth));
        LocalDate date = Instant.ofEpochMilli(fromMillis).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate candidate = date.withDayOfMonth(dom);
        long candidateMillis = candidate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        if (candidateMillis <= fromMillis) {
            candidate = date.plusMonths(1).withDayOfMonth(dom);
            candidateMillis = candidate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        }
        return candidateMillis;
    }
}
