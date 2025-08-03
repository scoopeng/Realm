package com.example.mongoexport;

import com.mongodb.client.*;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Test to identify binary/boolean fields that only contain null and false values
 */
public class TestBinaryFields {
    private static final Logger logger = LoggerFactory.getLogger(TestBinaryFields.class);
    
    public static void main(String[] args) {
        ExportConfig config = new ExportConfig();
        MongoClient mongoClient = MongoClients.create(config.getMongoUrl());
        MongoDatabase database = mongoClient.getDatabase(config.getDatabaseName());
        
        try {
            logger.info("=== Analyzing boolean fields in listings collection ===");
            analyzeCollection(database, "listings", 5000);
            
            logger.info("\n=== Analyzing boolean fields in properties collection ===");
            analyzeCollection(database, "properties", 5000);
            
            logger.info("\n=== Analyzing boolean fields in transactions collection ===");
            analyzeCollection(database, "transactions", 5000);
            
            logger.info("\n=== Analyzing boolean fields in currentAgents collection ===");
            analyzeCollection(database, "currentAgents", 1000);
            
        } catch (Exception e) {
            logger.error("Error during investigation", e);
        } finally {
            mongoClient.close();
        }
        
        logger.info("\nInvestigation complete!");
    }
    
    private static void analyzeCollection(MongoDatabase database, String collectionName, int sampleSize) {
        MongoCollection<Document> collection = database.getCollection(collectionName);
        
        // Map to track field values: fieldName -> (value -> count)
        Map<String, Map<Object, Integer>> fieldValueCounts = new HashMap<>();
        
        // First pass: collect all fields and their values
        int docCount = 0;
        for (Document doc : collection.find().limit(sampleSize)) {
            docCount++;
            analyzeDocument(doc, "", fieldValueCounts);
        }
        
        logger.info("Analyzed {} documents from {}", docCount, collectionName);
        
        // Second pass: identify boolean fields with only null/false
        List<String> suspiciousFields = new ArrayList<>();
        
        for (Map.Entry<String, Map<Object, Integer>> entry : fieldValueCounts.entrySet()) {
            String fieldName = entry.getKey();
            Map<Object, Integer> valueCounts = entry.getValue();
            
            // Check if this appears to be a boolean field
            boolean isBooleanField = false;
            boolean hasTrue = false;
            boolean hasFalse = false;
            boolean hasOtherValues = false;
            
            for (Object value : valueCounts.keySet()) {
                if (value instanceof Boolean) {
                    isBooleanField = true;
                    if ((Boolean) value) {
                        hasTrue = true;
                    } else {
                        hasFalse = true;
                    }
                } else if (value != null) {
                    hasOtherValues = true;
                }
            }
            
            // If it's a boolean field with only false/null values, it's suspicious
            if (isBooleanField && !hasTrue && !hasOtherValues) {
                suspiciousFields.add(fieldName);
                
                // Log details
                logger.info("\nField '{}' only contains null/false:", fieldName);
                for (Map.Entry<Object, Integer> valueEntry : valueCounts.entrySet()) {
                    Object value = valueEntry.getKey();
                    Integer count = valueEntry.getValue();
                    logger.info("  {} : {} occurrences", value == null ? "null" : value, count);
                }
            }
        }
        
        if (suspiciousFields.isEmpty()) {
            logger.info("No boolean fields found with only null/false values");
        } else {
            logger.info("\nSummary - Fields with only null/false values:");
            for (String field : suspiciousFields) {
                logger.info("  - {}", field);
            }
        }
    }
    
    private static void analyzeDocument(Document doc, String prefix, Map<String, Map<Object, Integer>> fieldValueCounts) {
        for (String key : doc.keySet()) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = doc.get(key);
            
            // Skip certain fields
            if (key.equals("_id") || key.equals("__v")) continue;
            
            // For nested documents, recurse
            if (value instanceof Document && !key.equals("address") && !key.equals("location")) {
                analyzeDocument((Document) value, fullKey, fieldValueCounts);
            } else if (value instanceof Boolean || value == null) {
                // Track boolean and null values
                fieldValueCounts.computeIfAbsent(fullKey, k -> new HashMap<>())
                    .merge(value, 1, Integer::sum);
            }
        }
    }
}