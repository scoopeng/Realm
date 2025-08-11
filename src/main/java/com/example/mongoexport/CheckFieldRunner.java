package com.example.mongoexport;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckFieldRunner {
    private static final Logger logger = LoggerFactory.getLogger(CheckFieldRunner.class);
    
    public static void main(String[] args) {
        ExportConfig config = new ExportConfig();
        
        try (MongoClient mongoClient = MongoClients.create(config.getMongoUrl())) {
            MongoDatabase database = mongoClient.getDatabase(config.getDatabaseName());
            MongoCollection<Document> collection = database.getCollection("listings");
            
            logger.info("=== Checking belowGradeAreaFinished field ===");
            
            // Check first 10 documents
            logger.info("\nFirst 10 documents:");
            int count = 0;
            for (Document doc : collection.find().limit(10)) {
                Object value = doc.get("belowGradeAreaFinished");
                logger.info("Doc {}: belowGradeAreaFinished = {} (type: {})", 
                    ++count, value, value != null ? value.getClass().getSimpleName() : "null");
            }
            
            // Find one with non-null value
            logger.info("\nSearching for non-null belowGradeAreaFinished...");
            Document query = new Document("belowGradeAreaFinished", new Document("$exists", true).append("$ne", null));
            Document docWithValue = collection.find(query).first();
            
            if (docWithValue != null) {
                logger.info("Found document with belowGradeAreaFinished:");
                logger.info("  Value: {}", docWithValue.get("belowGradeAreaFinished"));
                logger.info("  Type: {}", docWithValue.get("belowGradeAreaFinished").getClass());
                
                // Show all keys to understand document structure
                logger.info("  Document keys: {}", docWithValue.keySet());
            } else {
                logger.info("No documents found with non-null belowGradeAreaFinished");
                
                // Try to find ANY document that has the field (even if null)
                Document existsQuery = new Document("belowGradeAreaFinished", new Document("$exists", true));
                Document docWithField = collection.find(existsQuery).first();
                if (docWithField != null) {
                    logger.info("Found document with belowGradeAreaFinished field (but value is null)");
                    logger.info("  Document keys: {}", docWithField.keySet());
                }
            }
            
            // Count documents with this field
            long totalDocs = collection.countDocuments();
            long docsWithField = collection.countDocuments(query);
            logger.info("\nStatistics:");
            logger.info("  Total documents: {}", totalDocs);
            logger.info("  Documents with non-null belowGradeAreaFinished: {}", docsWithField);
            logger.info("  Percentage: {}%", docsWithField * 100.0 / totalDocs);
        }
    }
}