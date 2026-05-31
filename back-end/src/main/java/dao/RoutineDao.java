package dao;

import com.mongodb.client.MongoCollection;
import dto.RoutineDto;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RoutineDao extends BaseDao<RoutineDto> {
    private static RoutineDao instance;

    private RoutineDao(MongoCollection<Document> coll) { super(coll); }

    public static RoutineDao getInstance() {
        if (instance != null) return instance;
        instance = new RoutineDao(MongoConnection.getCollection("Routines"));
        return instance;
    }

    public static RoutineDao getInstance(MongoCollection<Document> coll) {
        instance = new RoutineDao(coll);
        return instance;
    }

    @Override
    public List<RoutineDto> query(Document filter) {
        return collection.find(filter)
                .into(new ArrayList<>())
                .stream()
                .map(RoutineDto::fromDocument)
                .collect(Collectors.toList());
    }

    public List<RoutineDto> listForUser(String userName) {
        return query(new Document("userName", userName));
    }

    public RoutineDto findByIdForUser(ObjectId id, String userName) {
        Document doc = collection.find(new Document("_id", id).append("userName", userName)).first();
        return doc == null ? null : RoutineDto.fromDocument(doc);
    }

    /** Active routines whose nextRunAt is at or before {@code nowMillis}. */
    public List<RoutineDto> findDue(long nowMillis) {
        return query(new Document("active", true)
                .append("nextRunAtMillis", new Document("$lte", nowMillis)));
    }

    public boolean deleteForUser(ObjectId id, String userName) {
        return collection.deleteOne(new Document("_id", id).append("userName", userName)).getDeletedCount() == 1;
    }

    public void recordRun(ObjectId id, long nextRunAtMillis, long lastRunAtMillis, String status) {
        collection.updateOne(new Document("_id", id),
                new Document("$set", new Document("nextRunAtMillis", nextRunAtMillis)
                        .append("lastRunAtMillis", lastRunAtMillis)
                        .append("lastStatus", status)));
    }
}
