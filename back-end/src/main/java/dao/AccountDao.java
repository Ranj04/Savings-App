package dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import dto.AccountDto;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AccountDao extends BaseDao<AccountDto> {
    private static AccountDao instance;
    private AccountDao(MongoCollection<Document> coll) { super(coll); }

    public static AccountDao getInstance() {
        if (instance != null) return instance;
        instance = new AccountDao(MongoConnection.getCollection("Account"));
        return instance;
    }

    public static AccountDao getInstance(MongoCollection<Document> coll) {
        instance = new AccountDao(coll);
        return instance;
    }

    @Override
    public List<AccountDto> query(Document filter) {
        return collection.find(filter)
                .into(new ArrayList<>())
                .stream()
                .map(AccountDto::fromDocument)
                .collect(Collectors.toList());
    }

    public void replace(ObjectId id, AccountDto dto) {
        collection.replaceOne(new Document("_id", id), dto.toDocument());
    }

    public AccountDto findByIdForUser(ObjectId id, String userName) {
        Document doc = collection.find(new Document("_id", id).append("userName", userName)).first();
        return doc == null ? null : AccountDto.fromDocument(doc);
    }

    public AccountDto findByNameForUser(String accountName, String userName) {
        Document doc = collection.find(new Document("name", accountName).append("userName", userName)).first();
        return doc == null ? null : AccountDto.fromDocument(doc);
    }

    public AccountDto findByPlaidAccountId(String plaidAccountId, String userName) {
        Document doc = collection.find(
                new Document("plaidAccountId", plaidAccountId).append("userName", userName)).first();
        return doc == null ? null : AccountDto.fromDocument(doc);
    }

    /**
     * Atomically earmark {@code amount} of this account's *usable* money into goals,
     * by raising sumAllocated — but only if usable (balance - sumAllocated) >= amount.
     * Returns true iff the reservation succeeded. Safe under concurrency: the guard
     * and the increment happen in a single document update.
     */
    public boolean tryReserve(ObjectId id, String userName, double amount) {
        Document filter = new Document("_id", id).append("userName", userName)
                .append("$expr", new Document("$gte", List.of(
                        new Document("$subtract", List.of(
                                new Document("$ifNull", List.of("$balance", 0)),
                                new Document("$ifNull", List.of("$sumAllocated", 0)))),
                        amount)));
        Document update = new Document("$inc", new Document("sumAllocated", amount).append("version", 1L));
        UpdateResult r = collection.updateOne(filter, update);
        return r.getModifiedCount() == 1;
    }

    /** Release a previously-reserved amount back to the usable pool (never below 0). */
    public void releaseReservation(ObjectId id, String userName, double amount) {
        // Only decrement as far as 0 to avoid a negative sumAllocated on drift.
        Document filter = new Document("_id", id).append("userName", userName)
                .append("$expr", new Document("$gte", List.of(
                        new Document("$ifNull", List.of("$sumAllocated", 0)), amount)));
        Document update = new Document("$inc", new Document("sumAllocated", -amount).append("version", 1L));
        UpdateResult r = collection.updateOne(filter, update);
        if (r.getModifiedCount() != 1) {
            // Drift fallback: clamp sumAllocated to 0.
            collection.updateOne(new Document("_id", id).append("userName", userName),
                    new Document("$set", new Document("sumAllocated", 0.0))
                            .append("$inc", new Document("version", 1L)));
        }
    }

    /** Atomically move usable money out of this account (for account-to-account transfers). */
    public boolean tryDebitUsable(ObjectId id, String userName, double amount) {
        Document filter = new Document("_id", id).append("userName", userName)
                .append("$expr", new Document("$gte", List.of(
                        new Document("$subtract", List.of(
                                new Document("$ifNull", List.of("$balance", 0)),
                                new Document("$ifNull", List.of("$sumAllocated", 0)))),
                        amount)));
        Document update = new Document("$inc", new Document("balance", -amount).append("version", 1L));
        UpdateResult r = collection.updateOne(filter, update);
        return r.getModifiedCount() == 1;
    }

    public void creditBalance(ObjectId id, String userName, double amount) {
        collection.updateOne(new Document("_id", id).append("userName", userName),
                new Document("$inc", new Document("balance", amount).append("version", 1L)));
    }

    /** Set an account's absolute balance (used when refreshing a Plaid-linked account). */
    public void setBalance(ObjectId id, String userName, double balance) {
        collection.updateOne(new Document("_id", id).append("userName", userName),
                new Document("$set", new Document("balance", balance))
                        .append("$inc", new Document("version", 1L)));
    }

    /** Self-heal: set sumAllocated to the authoritative sum computed from goals. */
    public void reconcileSumAllocated(ObjectId id, String userName, double actualSum) {
        collection.updateOne(new Document("_id", id).append("userName", userName),
                new Document("$set", new Document("sumAllocated", actualSum)));
    }
}
