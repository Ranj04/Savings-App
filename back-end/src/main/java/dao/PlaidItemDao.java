package dao;

import com.mongodb.client.MongoCollection;
import dto.PlaidItemDto;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PlaidItemDao extends BaseDao<PlaidItemDto> {
    private static PlaidItemDao instance;

    private PlaidItemDao(MongoCollection<Document> coll) { super(coll); }

    public static PlaidItemDao getInstance() {
        if (instance != null) return instance;
        instance = new PlaidItemDao(MongoConnection.getCollection("PlaidItems"));
        return instance;
    }

    public static PlaidItemDao getInstance(MongoCollection<Document> coll) {
        instance = new PlaidItemDao(coll);
        return instance;
    }

    @Override
    public List<PlaidItemDto> query(Document filter) {
        return collection.find(filter)
                .into(new ArrayList<>())
                .stream()
                .map(PlaidItemDto::fromDocument)
                .collect(Collectors.toList());
    }

    public List<PlaidItemDto> findByUser(String userName) {
        return query(new Document("userName", userName));
    }
}
