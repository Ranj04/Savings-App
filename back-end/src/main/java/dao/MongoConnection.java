package dao;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.concurrent.TimeUnit;
import org.bson.Document;

public class MongoConnection {

    private static final String mongoUrl = System.getenv().getOrDefault("MONGO_URL", "mongodb://localhost:27017");
    private static final String mongoDb  = System.getenv().getOrDefault("MONGO_DB",  "Homework2");

    // Fail fast (and loudly) when Mongo is unreachable instead of blocking on the
    // driver's default 30s server-selection timeout — that silent hang shows up in
    // the UI as a generic "Request timed out" (the frontend aborts at 10s). A 5s
    // cap means a misconfigured MONGO_URL / Atlas IP allowlist surfaces as a real
    // logged 500 well within the frontend's window.
    private static final MongoClient mongoClient = MongoClients.create(
            MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(mongoUrl))
                    .applyToClusterSettings(b -> b.serverSelectionTimeout(5, TimeUnit.SECONDS))
                    .build());

    public static MongoDatabase getDb() {
        return mongoClient.getDatabase(mongoDb);
    }

    public static MongoCollection<Document> getCollection(String collectionName) {
        return getDb().getCollection(collectionName);
    }

}
