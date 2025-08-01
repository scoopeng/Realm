package com.example.mongoexport;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class InspectCollections {
    private static final Logger logger = LoggerFactory.getLogger(InspectCollections.class);
    
    public static void main(String[] args) {
        ExportConfig config = new ExportConfig();
        
        try (MongoClient mongoClient = MongoClients.create(config.getMongoUrl())) {
            MongoDatabase database = mongoClient.getDatabase(config.getDatabaseName());
            
            String[] collections = {"agentclients", "agents", "properties", "people"};
            
            for (String collectionName : collections) {
                logger.info("\n=== Inspecting collection: {} ===", collectionName);
                MongoCollection<Document> collection = database.getCollection(collectionName);
                
                // Get first document to inspect structure
                Document firstDoc = collection.find().limit(1).first();
                
                if (firstDoc != null) {
                    logger.info("Sample document structure:");
                    Set<String> fields = new TreeSet<>(firstDoc.keySet());
                    for (String field : fields) {
                        Object value = firstDoc.get(field);
                        String type = value != null ? value.getClass().getSimpleName() : "null";
                        
                        if (value instanceof List) {
                            logger.info("  - {} (List with {} items)", field, ((List<?>) value).size());
                        } else if (value instanceof Document) {
                            logger.info("  - {} (Document with fields: {})", field, ((Document) value).keySet());
                        } else {
                            logger.info("  - {} ({})", field, type);
                        }
                    }
                    
                    // Count documents
                    long count = collection.countDocuments();
                    logger.info("Total documents: {}", count);
                } else {
                    logger.warn("Collection {} is empty", collectionName);
                }
            }
        }
    }
}