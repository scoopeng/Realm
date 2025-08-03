package com.example.mongoexport;

import com.mongodb.client.*;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Test to understand actual listing document structure
 */
public class TestListingStructure {
    private static final Logger logger = LoggerFactory.getLogger(TestListingStructure.class);
    
    public static void main(String[] args) {
        ExportConfig config = new ExportConfig();
        MongoClient mongoClient = MongoClients.create(config.getMongoUrl());
        MongoDatabase database = mongoClient.getDatabase(config.getDatabaseName());
        
        try {
            logger.info("=== Analyzing listing document structure ===");
            MongoCollection<Document> listings = database.getCollection("listings");
            
            // Get a few sample listings
            int count = 0;
            for (Document listing : listings.find().limit(3)) {
                logger.info("\n--- Listing {} ---", ++count);
                logger.info("ID: {}", listing.getObjectId("_id"));
                
                // Show all top-level keys
                logger.info("Top-level keys:");
                for (String key : listing.keySet()) {
                    Object value = listing.get(key);
                    String valuePreview = "";
                    if (value instanceof Document) {
                        valuePreview = " (Document with " + ((Document) value).keySet().size() + " keys)";
                    } else if (value instanceof List) {
                        valuePreview = " (List with " + ((List<?>) value).size() + " items)";
                    } else if (value != null) {
                        String str = value.toString();
                        if (str.length() > 50) {
                            str = str.substring(0, 47) + "...";
                        }
                        valuePreview = " = " + str;
                    }
                    logger.info("  {}{}", key, valuePreview);
                }
                
                // Check specifically for features-related fields
                logger.info("\nChecking for feature-related fields:");
                Document features = (Document) listing.get("features");
                logger.info("  features: {}", features);
                
                // Check if features might be elsewhere
                Object propertyRef = listing.get("property");
                if (propertyRef != null) {
                    logger.info("  property field type: {} value: {}", 
                        propertyRef.getClass().getSimpleName(), propertyRef);
                }
                
                // Check realmData
                Document realmData = (Document) listing.get("realmData");
                if (realmData != null) {
                    logger.info("  realmData document keys (first 10): {}", 
                        realmData.keySet().stream().limit(10).toArray());
                    Document realmFeatures = (Document) realmData.get("features");
                    logger.info("  realmData.features: {}", realmFeatures);
                }
            }
            
            // Now check if any listings have features at all
            logger.info("\n=== Checking for any listings with features ===");
            long withFeatures = listings.countDocuments(new Document("features", new Document("$exists", true)));
            logger.info("Listings with 'features' field: {}", withFeatures);
            
            long withPropertyFeatures = listings.countDocuments(new Document("property.features", new Document("$exists", true)));
            logger.info("Listings with 'property.features' field: {}", withPropertyFeatures);
            
            long withRealmFeatures = listings.countDocuments(new Document("realmData.features", new Document("$exists", true)));
            logger.info("Listings with 'realmData.features' field: {}", withRealmFeatures);
            
        } catch (Exception e) {
            logger.error("Error during investigation", e);
        } finally {
            mongoClient.close();
        }
        
        logger.info("\nInvestigation complete!");
    }
}