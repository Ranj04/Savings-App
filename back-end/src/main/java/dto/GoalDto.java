package dto;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GoalDto extends BaseDto {
    public ObjectId id; // renamed from _id for style compliance
    public String userName;        // owner
    public String type;            // "savings" | "spending"
    public String name;
    public Double targetAmount;    // savings target or spending limit
    public String category;        // only for spending goals (e.g., "Food")
    public Long  dueDateMillis;    // optional for savings
    public Long  createdAt;
    public Boolean active = true;
    public List<Contribution> contributions = new ArrayList<>(); // for savings
    // Newly added fields
    public org.bson.types.ObjectId accountId; // parent account
    public Double allocatedAmount;            // default 0.0 for savings envelopes

    public static class Contribution {
        public Double amount;
        public Long   dateMillis;
        public String note;

        public Document toDocument() {
            return new Document()
                    .append("amount", amount)
                    .append("dateMillis", dateMillis)
                    .append("note", note);
        }

        public static Contribution fromDocument(Document d) {
            Contribution c = new Contribution();
            c.amount = d.getDouble("amount");
            c.dateMillis = d.getLong("dateMillis");
            c.note = d.getString("note");
            return c;
        }
    }

    @Override
    public Document toDocument() {
        List<Document> contribDocs = contributions == null ? null : contributions.stream()
                .map(Contribution::toDocument)
                .collect(Collectors.toList());
        return new Document()
                .append("userName", userName)
                .append("type", type)
                .append("name", name)
                .append("targetAmount", targetAmount)
                .append("category", category)
                .append("dueDateMillis", dueDateMillis)
                .append("createdAt", createdAt)
                .append("active", active)
                .append("contributions", contribDocs)
                .append("accountId", accountId)
                .append("allocatedAmount", allocatedAmount == null ? 0.0 : allocatedAmount);
    }

    public static GoalDto fromDocument(Document d) {
        GoalDto g = new GoalDto();
        if (d.getObjectId("_id") != null) {
            g.id = d.getObjectId("_id");
            g.loadUniqueId(d);
        }
        g.userName = d.getString("userName");
        g.type = d.getString("type");
        g.name = d.getString("name");
        g.targetAmount = d.getDouble("targetAmount");
        g.category = d.getString("category");
        g.dueDateMillis = d.getLong("dueDateMillis");
        g.createdAt = d.getLong("createdAt");
        g.active = d.getBoolean("active", true);
        var contribList = d.get("contributions", List.class);
        if (contribList != null) {
            for (Object o : contribList) {
                if (o instanceof Document) {
                    g.contributions.add(Contribution.fromDocument((Document) o));
                }
            }
        }
        // Robust allocatedAmount defaulting
        Object alloc = d.get("allocatedAmount");
        if (alloc instanceof Number) {
            g.allocatedAmount = ((Number) alloc).doubleValue();
        } else {
            g.allocatedAmount = 0.0;
        }
        // accountId may be null for legacy docs
        g.accountId = d.getObjectId("accountId");
        return g;
    }
}
