package com.example.mongoexport;

import com.mongodb.client.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.*;

public class CheckPeopleVsPeopleMeta {
    public static void main(String[] args) {
        String url = "mongodb://scoopanalytics:Jackson09*@3.142.74.73:27017/?authSource=admin";
        try (MongoClient client = MongoClients.create(url)) {
            MongoDatabase db = client.getDatabase("realm");
            
            // Check if both collections exist
            List<String> collections = db.listCollectionNames().into(new ArrayList<>());
            boolean hasPeople = collections.contains("people");
            boolean hasPeopleMeta = collections.contains("people_meta");
            
            System.out.println("=== Collection Status ===");
            System.out.println("'people' exists: " + hasPeople);
            System.out.println("'people_meta' exists: " + hasPeopleMeta);
            
            // Get some client IDs from agentclients
            MongoCollection<Document> agentClients = db.getCollection("agentclients");
            List<ObjectId> clientIds = new ArrayList<>();
            
            System.out.println("\n=== Getting client IDs from agentclients ===");
            for (Document doc : agentClients.find().limit(10)) {
                Object clientId = doc.get("client");
                if (clientId instanceof ObjectId) {
                    clientIds.add((ObjectId) clientId);
                }
            }
            System.out.println("Found " + clientIds.size() + " client IDs");
            
            if (hasPeopleMeta) {
                System.out.println("\n=== Checking people_meta ===");
                MongoCollection<Document> peopleMeta = db.getCollection("people_meta");
                System.out.println("Total docs: " + peopleMeta.estimatedDocumentCount());
                
                int found = 0;
                int withAddress = 0;
                for (ObjectId id : clientIds) {
                    Document doc = peopleMeta.find(new Document("_id", id)).first();
                    if (doc != null) {
                        found++;
                        if (doc.containsKey("address")) {
                            withAddress++;
                            System.out.println("  Found address in people_meta for ID: " + id);
                        }
                    }
                }
                System.out.println("Found " + found + "/" + clientIds.size() + " in people_meta");
                System.out.println("With address: " + withAddress);
            }
            
            if (hasPeople) {
                System.out.println("\n=== Checking people ===");
                MongoCollection<Document> people = db.getCollection("people");
                System.out.println("Total docs: " + people.estimatedDocumentCount());
                
                int found = 0;
                int withAddress = 0;
                for (ObjectId id : clientIds) {
                    Document doc = people.find(new Document("_id", id)).first();
                    if (doc != null) {
                        found++;
                        if (doc.containsKey("address")) {
                            withAddress++;
                            System.out.println("  Found address in people for ID: " + id);
                            // Show sample address
                            if (withAddress == 1) {
                                Document addr = (Document) doc.get("address");
                                if (addr != null) {
                                    System.out.println("    Address keys: " + addr.keySet());
                                    System.out.println("    City: " + addr.get("city"));
                                }
                            }
                        }
                    }
                }
                System.out.println("Found " + found + "/" + clientIds.size() + " in people");
                System.out.println("With address: " + withAddress);
            }
            
            // Check which collection RelationshipDiscovery would choose
            System.out.println("\n=== Testing RelationshipDiscovery logic ===");
            if (!clientIds.isEmpty()) {
                ObjectId testId = clientIds.get(0);
                System.out.println("Testing with ID: " + testId);
                
                if (hasPeopleMeta) {
                    Document inPeopleMeta = db.getCollection("people_meta")
                        .find(new Document("_id", testId)).first();
                    System.out.println("Found in people_meta: " + (inPeopleMeta != null));
                }
                
                if (hasPeople) {
                    Document inPeople = db.getCollection("people")
                        .find(new Document("_id", testId)).first();
                    System.out.println("Found in people: " + (inPeople != null));
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}