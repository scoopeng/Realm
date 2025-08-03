package com.example.mongoexport;

import com.mongodb.client.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Test to analyze UltraTransactionExporter fields for:
 * 1. Foreign key fields that need resolution
 * 2. Empty/null fields that should be removed
 * 3. Binary fields with only false/null values
 */
public class TestTransactionFields {
    private static final Logger logger = LoggerFactory.getLogger(TestTransactionFields.class);
    
    public static void main(String[] args) {
        ExportConfig config = new ExportConfig();
        MongoClient mongoClient = MongoClients.create(config.getMongoUrl());
        MongoDatabase database = mongoClient.getDatabase(config.getDatabaseName());
        
        try {
            logger.info("=== Analyzing Transaction Collection Fields ===");
            MongoCollection<Document> transactions = database.getCollection("transactions");
            
            // Analyze field types and values
            Map<String, Set<String>> fieldTypes = new HashMap<>();
            Map<String, Map<Object, Integer>> fieldValues = new HashMap<>();
            Map<String, Integer> nullCounts = new HashMap<>();
            Map<String, Integer> totalCounts = new HashMap<>();
            
            int docCount = 0;
            for (Document transaction : transactions.find().limit(5000)) {
                docCount++;
                analyzeDocument(transaction, "", fieldTypes, fieldValues, nullCounts, totalCounts);
            }
            
            logger.info("Analyzed {} transaction documents", docCount);
            
            // Report foreign key fields (ObjectId references)
            logger.info("\n=== Foreign Key Fields (ObjectId references) ===");
            for (Map.Entry<String, Set<String>> entry : fieldTypes.entrySet()) {
                if (entry.getValue().contains("ObjectId")) {
                    String fieldName = entry.getKey();
                    logger.info("Field '{}' contains ObjectId references", fieldName);
                    
                    // Show sample values
                    Map<Object, Integer> values = fieldValues.get(fieldName);
                    if (values != null) {
                        int shown = 0;
                        for (Object value : values.keySet()) {
                            if (value instanceof ObjectId && shown++ < 3) {
                                logger.info("  Sample: {}", value);
                            }
                        }
                    }
                }
            }
            
            // Report fields that are mostly null
            logger.info("\n=== Fields with High Null Percentage ===");
            for (String field : totalCounts.keySet()) {
                int total = totalCounts.get(field);
                int nulls = nullCounts.getOrDefault(field, 0);
                double nullPercentage = (nulls * 100.0) / total;
                
                if (nullPercentage > 90 && !field.contains("_id")) {
                    logger.info("Field '{}': {:.1f}% null ({}/{} documents)", 
                        field, nullPercentage, nulls, total);
                }
            }
            
            // Report boolean fields with only false/null
            logger.info("\n=== Boolean Fields with Only False/Null ===");
            for (Map.Entry<String, Map<Object, Integer>> entry : fieldValues.entrySet()) {
                String fieldName = entry.getKey();
                Map<Object, Integer> values = entry.getValue();
                
                boolean hasTrue = values.containsKey(true);
                boolean hasFalse = values.containsKey(false);
                boolean hasOtherValues = values.keySet().stream()
                    .anyMatch(v -> v != null && !(v instanceof Boolean));
                
                if ((hasFalse || nullCounts.getOrDefault(fieldName, 0) > 0) && !hasTrue && !hasOtherValues) {
                    logger.info("Field '{}' only contains false/null values", fieldName);
                    for (Map.Entry<Object, Integer> valueEntry : values.entrySet()) {
                        logger.info("  {} : {} occurrences", 
                            valueEntry.getKey() == null ? "null" : valueEntry.getKey(), 
                            valueEntry.getValue());
                    }
                }
            }
            
            // Check specific fields used in the exporter
            logger.info("\n=== Checking Specific Transaction Fields ===");
            String[] specificFields = {
                "listing", "buyers", "sellers", "buyerAgent", "listingAgent",
                "sellingAgent", "sellingOffice", "listingOffice", "deleted",
                "closingDate", "price", "financing", "escrow"
            };
            
            for (String field : specificFields) {
                if (fieldTypes.containsKey(field)) {
                    Set<String> types = fieldTypes.get(field);
                    int nullCount = nullCounts.getOrDefault(field, 0);
                    int totalCount = totalCounts.getOrDefault(field, 0);
                    double nullPct = (nullCount * 100.0) / totalCount;
                    
                    logger.info("Field '{}': Types={}, Null={:.1f}% ({}/{})", 
                        field, types, nullPct, nullCount, totalCount);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error during investigation", e);
        } finally {
            mongoClient.close();
        }
        
        logger.info("\nInvestigation complete!");
    }
    
    private static void analyzeDocument(Document doc, String prefix, 
                                       Map<String, Set<String>> fieldTypes,
                                       Map<String, Map<Object, Integer>> fieldValues,
                                       Map<String, Integer> nullCounts,
                                       Map<String, Integer> totalCounts) {
        for (String key : doc.keySet()) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = doc.get(key);
            
            // Track total count
            totalCounts.merge(fullKey, 1, Integer::sum);
            
            // Track null count
            if (value == null) {
                nullCounts.merge(fullKey, 1, Integer::sum);
            }
            
            // Track type
            String type = value == null ? "null" : value.getClass().getSimpleName();
            fieldTypes.computeIfAbsent(fullKey, k -> new HashSet<>()).add(type);
            
            // For certain types, track values
            if (value == null || value instanceof Boolean || value instanceof ObjectId) {
                fieldValues.computeIfAbsent(fullKey, k -> new HashMap<>())
                    .merge(value, 1, Integer::sum);
            }
            
            // Recurse into nested documents (but not too deep)
            if (value instanceof Document && prefix.split("\\.").length < 2) {
                analyzeDocument((Document) value, fullKey, fieldTypes, fieldValues, nullCounts, totalCounts);
            }
        }
    }
}