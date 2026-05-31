package dao;

import com.mongodb.client.MongoCollection;
import dto.AuthDto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;

public class AuthDao extends BaseDao<AuthDto> {

    private static AuthDao instance;

    private AuthDao(MongoCollection<Document> collection) {
        super(collection);
    }

    public static AuthDao getInstance() {
        if (instance != null) {
            return instance;
        }
        instance = new AuthDao(MongoConnection.getCollection("AuthDao"));
        return instance;
    }

    public static AuthDao getInstance(MongoCollection<Document> collection) {
        instance = new AuthDao(collection);
        return instance;
    }

    @Override
    public List<AuthDto> query(Document filter) {
        return collection.find(filter)
                .into(new ArrayList<>())
                .stream()
                .map(AuthDto::fromDocument)
                .collect(Collectors.toList());
    }

    /** Revoke a session token so a leaked/used cookie can no longer authenticate. */
    public void deleteByHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return;
        }
        collection.deleteMany(new Document("hash", hash));
    }
}
