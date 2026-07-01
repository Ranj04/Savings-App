package dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.result.UpdateResult;
import dto.GoalDto;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GoalDao extends BaseDao<GoalDto> {
    private static GoalDao instance;

    private GoalDao(MongoCollection<Document> collection) {
        super(collection);
        // Drop a legacy index on (userName, accountName, name) if it still exists.
        // The old key referenced `accountName`, which GoalDto never persists, so the
        // uniqueness constraint effectively collapsed to (userName, null, name) and
        // prevented users from having the same goal name under two different accounts.
        try {
            collection.dropIndex(new Document("userName", 1)
                    .append("accountName", 1)
                    .append("name", 1));
        } catch (Exception ignored) {
            // Index did not exist — nothing to do.
        }
        // Enforce one goal name per (user, account). Best-effort: if Mongo is
        // unreachable at boot, don't let an index build crash the whole server
        // (it used to throw straight out of Server.main). The app still boots and
        // serves /health; DB ops recover once Mongo is reachable.
        try {
            collection.createIndex(
                new Document("userName", 1)
                    .append("accountId", 1)
                    .append("name", 1),
                new IndexOptions().unique(true)
            );
        } catch (Exception e) {
            System.err.println("[GoalDao] deferred unique-index creation (Mongo not ready): " + e.getMessage());
        }
    }

    public static GoalDao getInstance() {
        if (instance == null) {
            instance = new GoalDao(MongoConnection.getCollection("Goals"));
        }
        return instance;
    }

    // Test seam: inject a (mock) collection.
    public static GoalDao getInstance(MongoCollection<Document> coll) {
        instance = new GoalDao(coll);
        return instance;
    }

    @Override
    public List<GoalDto> query(Document filter) {
        return collection.find(filter)
                .into(new ArrayList<>())
                .stream()
                .map(GoalDto::fromDocument)
                .collect(Collectors.toList());
    }

    public GoalDto byIdForUser(ObjectId id, String user) {
        Document filter = new Document("_id", id).append("userName", user);
        Document doc = collection.find(filter).first();
        return doc == null ? null : GoalDto.fromDocument(doc);
    }

    public void replace(ObjectId id, GoalDto goal) {
        collection.replaceOne(new Document("_id", id), goal.toDocument());
    }

    /** Atomically add to a goal's allocated amount (null-safe for legacy docs). */
    public void incAllocated(ObjectId goalId, String userName, double amount) {
        // Pipeline update so a missing/null allocatedAmount is treated as 0.
        collection.updateOne(
                new Document("_id", goalId).append("userName", userName),
                List.of(new Document("$set", new Document("allocatedAmount",
                        new Document("$add", List.of(
                                new Document("$ifNull", List.of("$allocatedAmount", 0)), amount))))));
    }

    /**
     * Atomically remove {@code amount} from a goal's allocation, but only if it has at
     * least that much allocated. Returns true iff the decrement happened.
     */
    public boolean tryDecAllocated(ObjectId goalId, String userName, double amount) {
        Document filter = new Document("_id", goalId).append("userName", userName)
                .append("$expr", new Document("$gte", List.of(
                        new Document("$ifNull", List.of("$allocatedAmount", 0)), amount)));
        UpdateResult r = collection.updateOne(filter,
                new Document("$inc", new Document("allocatedAmount", -amount)));
        return r.getModifiedCount() == 1;
    }

    public GoalDto delete(Document filter) {
        Document doc = collection.find(filter).first();
        if (doc == null) return null;
        collection.deleteOne(filter);
        return GoalDto.fromDocument(doc);
    }

    // New method: find goals by userName
    public List<GoalDto> findByUser(String userName) {
        return collection.find(new Document("userName", userName))
                .into(new ArrayList<>())
                .stream()
                .map(GoalDto::fromDocument)
                .collect(Collectors.toList());
    }
}
