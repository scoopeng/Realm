package com.example.mongoexport;

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
            
            // Random sample - check multiple offsets
            System.out.println("\nChecking different parts of collection:");
            int[] offsets = {10000, 50000, 100000, 200000, 300000, 400000, 500000};
            
            for (int offset : offsets) {
                int count = 0;
                for (Document doc : pm.find().skip(offset).limit(100)) {
                    if (doc.containsKey("address")) {
                        count++;
                    }
                }
                System.out.println("  Offset " + offset + ": " + count + "/100 have address");
            }
            
            // Check if "people" collection exists
            System.out.println("\n=== Checking for 'people' collection ===");
            boolean peopleExists = db.listCollectionNames()
                .into(new ArrayList<>())
                .contains("people");
            
            if (peopleExists) {
                MongoCollection<Document> people = db.getCollection("people");
                System.out.println("'people' collection exists with " + people.estimatedDocumentCount() + " docs");
                
                // Check first 100 docs
                System.out.println("\nFirst 100 'people' docs:");
                int peopleWithAddress = 0;
                Set<String> peopleKeys = new HashSet<>();
                
                for (Document doc : people.find().limit(100)) {
                    peopleKeys.addAll(doc.keySet());
                    if (doc.containsKey("address")) {
                        peopleWithAddress++;
                    }
                }
                
                System.out.println("  With address field: " + peopleWithAddress + "/100");
                System.out.println("  All unique keys: " + peopleKeys);
            } else {
                System.out.println("'people' collection does NOT exist");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}