package dto;

import org.bson.Document;

public class AccountDto extends BaseDto {
    public String userName;        // owner
    public String name;            // display label
    public String type;            // "savings" | "spending" | "checking"
    public Double balance;         // total money in the account (default 0)
    public Double sumAllocated;    // money earmarked into goals (default 0); usable = balance - sumAllocated
    public Long createdAt;         // millis
    public Boolean active;         // default true

    // Source of the account / Plaid linkage (null source == manual in-app account).
    public String source;          // "manual" | "plaid"
    public String plaidItemId;     // Plaid item this account belongs to
    public String plaidAccountId;  // Plaid's account_id (stable id from Plaid)
    public String institutionName; // e.g. "Chase"
    public String mask;            // last 4 digits, e.g. "0000"

    // Optimistic-concurrency guard, bumped on every balance/allocation change.
    public Long version;

    @Override
    public Document toDocument() {
        return new Document()
                .append("userName", userName)
                .append("name", name)
                .append("type", type)
                .append("balance", balance)
                .append("sumAllocated", sumAllocated == null ? 0.0 : sumAllocated)
                .append("createdAt", createdAt)
                .append("active", active)
                .append("source", source)
                .append("plaidItemId", plaidItemId)
                .append("plaidAccountId", plaidAccountId)
                .append("institutionName", institutionName)
                .append("mask", mask)
                .append("version", version == null ? 0L : version);
    }

    public static AccountDto fromDocument(Document d) {
        var a = new AccountDto();
        if (d.get("_id") != null) a.loadUniqueId(d);
        a.userName = d.getString("userName");
        a.name = d.getString("name");
        a.type = d.getString("type");
        a.balance = asDouble(d.get("balance"));
        a.sumAllocated = asDouble(d.get("sumAllocated"));
        if (a.sumAllocated == null) a.sumAllocated = 0.0;
        a.createdAt = d.getLong("createdAt");
        a.active = d.getBoolean("active", true);
        a.source = d.getString("source");
        a.plaidItemId = d.getString("plaidItemId");
        a.plaidAccountId = d.getString("plaidAccountId");
        a.institutionName = d.getString("institutionName");
        a.mask = d.getString("mask");
        Object v = d.get("version");
        a.version = v instanceof Number ? ((Number) v).longValue() : 0L;
        return a;
    }

    /** Usable (unallocated) money in this account. */
    public double usable() {
        double bal = balance == null ? 0.0 : balance;
        double alloc = sumAllocated == null ? 0.0 : sumAllocated;
        return Math.max(0.0, bal - alloc);
    }

    private static Double asDouble(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : null;
    }
}
