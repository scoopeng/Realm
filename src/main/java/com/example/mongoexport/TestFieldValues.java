package com.example.mongoexport;

import com.mongodb.client.*;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Test to check unique values for fields that might be codes
 */
public class TestFieldValues {
    private static final Logger logger = LoggerFactory.getLogger(TestFieldValues.class);
    
    public static void main(String[] args) {
        ExportConfig config = new ExportConfig();
        MongoClient mongoClient = MongoClients.create(config.getMongoUrl());
        MongoDatabase database = mongoClient.getDatabase(config.getDatabaseName());
        
        try {
            // Check listings collection for type/code fields
            logger.info("=== Checking field values in listings collection ===");
            MongoCollection<Document> listings = database.getCollection("listings");
            
            // Fields to check
            String[] fieldsToCheck = {"listingType", "status", "lockboxType", "propertyType"};
            
            for (String field : fieldsToCheck) {
                logger.info("\nField: {}", field);
                Set<Object> uniqueValues = new HashSet<>();
                
                for (Document listing : listings.find().limit(1000)) {
                    Object value = listing.get(field);
                    if (value != null) {
                        uniqueValues.add(value);
                    }
                }
                
                logger.info("  Unique values (sample): ");
                int count = 0;
                for (Object value : uniqueValues) {
                    logger.info("    {} ({})", value, value.getClass().getSimpleName());
                    if (++count >= 10) {
                        if (uniqueValues.size() > 10) {
                            logger.info("    ... and {} more values", uniqueValues.size() - 10);
                        }
                        break;
                    }
                }
            }
            
            // Check properties collection
            logger.info("\n=== Checking field values in properties collection ===");
            MongoCollection<Document> properties = database.getCollection("properties");
            
            String[] propertyFields = {"propertyType", "propertySubType"};
            
            for (String field : propertyFields) {
                logger.info("\nField: {}", field);
                Set<Object> uniqueValues = new HashSet<>();
                
                for (Document property : properties.find().limit(1000)) {
                    Object value = property.get(field);
                    if (value != null) {
                        uniqueValues.add(value);
                    }
                }
                
                logger.info("  Unique values (sample): ");
                int count = 0;
                for (Object value : uniqueValues) {
                    logger.info("    {} ({})", value, value.getClass().getSimpleName());
                    if (++count >= 10) {
                        if (uniqueValues.size() > 10) {
                            logger.info("    ... and {} more values", uniqueValues.size() - 10);
                        }
                        break;
                    }
                }
            }
            
            // Check transactions for financing type
            logger.info("\n=== Checking financing type in transactions ===");
            MongoCollection<Document> transactions = database.getCollection("transactions");
            Set<Object> financingTypes = new HashSet<>();
            
            for (Document transaction : transactions.find().limit(1000)) {
                Object value = transaction.get("financingType");
                if (value != null) {
                    financingTypes.add(value);
                }
            }
            
            logger.info("Financing types found:");
            for (Object value : financingTypes) {
                logger.info("  {} ({})", value, value.getClass().getSimpleName());
            }
            
        } catch (Exception e) {
            logger.error("Error during investigation", e);
        } finally {
            mongoClient.close();
        }
        
        logger.info("\nInvestigation complete!");
    }
}