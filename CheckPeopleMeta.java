import com.mongodb.client.*;
import org.bson.Document;
import java.util.*;

public class CheckPeopleMeta {
    public static void main(String[] args) {
        String url = "mongodb://scoopanalytics:Jackson09*@3.142.74.73:27017/?authSource=admin";
        try (MongoClient client = MongoClients.create(url)) {
            MongoDatabase db = client.getDatabase("realm");
            MongoCollection<Document> pm = db.getCollection("people_meta");
            
            System.out.println("=== Analyzing people_meta collection ===");
            System.out.println("Total documents: " + pm.estimatedDocumentCount());
            
            // Check first 1000 docs for address field
            int withAddress = 0;
            int total = 0;
            Set<String> allKeys = new HashSet<>();
            
            for (Document doc : pm.find().limit(1000)) {
                total++;
                allKeys.addAll(doc.keySet());
                if (doc.containsKey("address")) {
                    withAddress++;
                    if (withAddress == 1) {
                        // Print first doc with address
                        System.out.println("\nFirst doc with address: " + doc.get("_id"));
                        Document addr = (Document) doc.get("address");
                        if (addr != null) {
                            System.out.println("  Address keys: " + addr.keySet());
                        }
                    }
                }
            }
            
            System.out.println("\nFirst 1000 docs:");
            System.out.println("  With address field: " + withAddress + "/" + total);
            System.out.println("  All unique keys found: " + allKeys);
            
            // Random sample
            System.out.println("\nRandom sample (skip 100K, take 100):");
            int randomWithAddress = 0;
            int randomTotal = 0;
            
            for (Document doc : pm.find().skip(100000).limit(100)) {
                randomTotal++;
                if (doc.containsKey("address")) {
                    randomWithAddress++;
                }
            }
            
            System.out.println("  With address field: " + randomWithAddress + "/" + randomTotal);
            
            // Check if "people" collection exists
            System.out.println("\n=== Checking for 'people' collection ===");
            boolean peopleExists = db.listCollectionNames()
                .into(new ArrayList<>())
                .contains("people");
            
            if (peopleExists) {
                MongoCollection<Document> people = db.getCollection("people");
                System.out.println("'people' collection exists with " + people.estimatedDocumentCount() + " docs");
                
                // Check first 10 docs
                System.out.println("\nFirst 10 'people' docs:");
                int peopleWithAddress = 0;
                Set<String> peopleKeys = new HashSet<>();
                
                for (Document doc : people.find().limit(10)) {
                    peopleKeys.addAll(doc.keySet());
                    if (doc.containsKey("address")) {
                        peopleWithAddress++;
                    }
                }
                
                System.out.println("  With address field: " + peopleWithAddress + "/10");
                System.out.println("  All unique keys: " + peopleKeys);
            } else {
                System.out.println("'people' collection does NOT exist");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}