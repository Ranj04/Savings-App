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

    static {
        // Atlas shared-tier (M0/free) clusters sit behind a multi-tenant proxy that
        // ROUTES BY SNI. If the JVM doesn't present the SNI extension, that proxy
        // aborts the TLS handshake with "Received fatal alert: internal_error" — which
        // is exactly what we saw on Railway. Force SNI on, and pin TLS 1.2 (JDK 21
        // defaults to 1.3, which that proxy has also been seen to reject). We set these
        // in-process, BEFORE the MongoClient below triggers JSSE, so they apply no
        // matter how the container is launched (a Dockerfile -D flag can be bypassed
        // by a platform custom start command; this can't).
        System.setProperty("jsse.enableSNIExtension", "true");
        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        String host = mongoUrl.replaceAll("://[^@]*@", "://****@"); // mask credentials
        System.out.println("[MongoConnection] TLS: SNI=on, protocols=TLSv1.2; target=" + host);
    }

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
