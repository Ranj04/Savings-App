package dto;

import org.bson.Document;

/**
 * A recurring monthly "set aside" instruction, e.g. "move $100 into the Hawaii
 * goal on the 1st of every month". Applied automatically by the scheduler, or
 * on-demand via the run endpoint.
 */
public class RoutineDto extends BaseDto {

    public String userName;
    public String name;            // label, e.g. "Hawaii vacation"
    public String accountId;       // hex string
    public String goalId;          // hex string
    public Double amount;
    public Integer dayOfMonth;     // 1..28 (clamped) — which day each month it runs
    public Boolean active = true;
    public Long nextRunAtMillis;   // when it should next fire
    public Long lastRunAtMillis;   // when it last fired
    public String lastStatus;      // "ok" or an error message from the last run
    public Long createdAt;

    @Override
    public Document toDocument() {
        return new Document()
                .append("userName", userName)
                .append("name", name)
                .append("accountId", accountId)
                .append("goalId", goalId)
                .append("amount", amount)
                .append("dayOfMonth", dayOfMonth)
                .append("active", active)
                .append("nextRunAtMillis", nextRunAtMillis)
                .append("lastRunAtMillis", lastRunAtMillis)
                .append("lastStatus", lastStatus)
                .append("createdAt", createdAt);
    }

    public static RoutineDto fromDocument(Document d) {
        RoutineDto r = new RoutineDto();
        if (d.get("_id") != null) r.loadUniqueId(d);
        r.userName = d.getString("userName");
        r.name = d.getString("name");
        r.accountId = d.getString("accountId");
        r.goalId = d.getString("goalId");
        Object amt = d.get("amount");
        r.amount = amt instanceof Number ? ((Number) amt).doubleValue() : null;
        Object dom = d.get("dayOfMonth");
        r.dayOfMonth = dom instanceof Number ? ((Number) dom).intValue() : null;
        r.active = d.getBoolean("active", true);
        r.nextRunAtMillis = d.getLong("nextRunAtMillis");
        r.lastRunAtMillis = d.getLong("lastRunAtMillis");
        r.lastStatus = d.getString("lastStatus");
        r.createdAt = d.getLong("createdAt");
        return r;
    }
}
