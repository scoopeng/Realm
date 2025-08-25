import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.*;

public class TestDataConsistency {
    public static void main(String[] args) {
        String connectionString = System.getProperty("mongodb.url", 
            "mongodb://scoopanalytics:Jackson09*@3.142.74.73:27017/?authSource=admin");
        
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase database = mongoClient.getDatabase("realm");
            
            // Get first 10 client ObjectIds from agentclients
            MongoCollection<Document> agentClients = database.getCollection("agentclients");
            List<ObjectId> clientIds = new ArrayList<>();
            
            System.out.println("=== Sampling first 10 agentclients documents ===");
            try (MongoCursor<Document> cursor = agentClients.find().limit(10).iterator()) {
                while (cursor.hasNext()) {
                    Document doc = cursor.next();
                    Object clientId = doc.get("client");
                    if (clientId instanceof ObjectId) {
                        clientIds.add((ObjectId) clientId);
                        System.out.println("Found client ID: " + clientId);
                    }
                }
            }
            
            System.out.println("\n=== Checking these IDs in people_meta collection ===");
            MongoCollection<Document> peopleMeta = database.getCollection("people_meta");
            int hasAddress = 0;
            for (ObjectId id : clientIds) {
                Document person = peopleMeta.find(new Document("_id", id)).first();
                if (person \!= null) {
                    System.out.println("\nDocument " + id + " fields: " + person.keySet());
                    if (person.containsKey("address")) {
                        System.out.println("  HAS ADDRESS FIELD");
                        hasAddress++;
                    } else {
                        System.out.println("  NO address field");
                    }
                }
            }
            System.out.println("Total with address: " + hasAddress + "/" + clientIds.size());
            
            // Check distribution
            System.out.println("\n=== Checking address distribution in people_meta ===");
            
            // Sequential: first 100 docs
            int sequentialWithAddress = 0;
            try (MongoCursor<Document> cursor = peopleMeta.find().limit(100).iterator()) {
                while (cursor.hasNext()) {
                    Document doc = cursor.next();
                    if (doc.containsKey("address")) {
                        sequentialWithAddress++;
                    }
                }
            }
            System.out.println("Sequential (first 100): " + sequentialWithAddress + " have address");
            
            // Random sample
            int randomWithAddress = 0;
            Random rand = new Random(42);
            for (int i = 0; i < 100; i++) {
                int skip = rand.nextInt(10000);
                Document doc = peopleMeta.find().skip(skip).limit(1).first();
                if (doc \!= null && doc.containsKey("address")) {
                    randomWithAddress++;
                }
            }
            System.out.println("Random sample (100 docs): " + randomWithAddress + " have address");
        }
    }
}
