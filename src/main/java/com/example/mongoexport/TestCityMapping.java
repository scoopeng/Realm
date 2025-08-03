package com.example.mongoexport;

import com.mongodb.client.*;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test utility to investigate city field structure in brokerages and usCities collections
 */
public class TestCityMapping {
    private static final Logger logger = LoggerFactory.getLogger(TestCityMapping.class);
    
    public static void main(String[] args) {
        ExportConfig config = new ExportConfig();
        MongoClient mongoClient = MongoClients.create(config.getMongoUrl());
        MongoDatabase database = mongoClient.getDatabase(config.getDatabaseName());
        
        logger.info("Investigating city field structures...");
        
        try {
            // Check brokerages collection
            logger.info("\n=== Brokerages Collection Sample ===");
            MongoCollection<Document> brokerages = database.getCollection("brokerages");
            int count = 0;
            for (Document brokerage : brokerages.find().limit(5)) {
                logger.info("Brokerage {}: name={}, city field={}", 
                    ++count, 
                    brokerage.getString("name"),
                    brokerage.get("city"));
                Object cityField = brokerage.get("city");
                if (cityField != null) {
                    logger.info("  City field type: {} value: {}", cityField.getClass().getSimpleName(), cityField);
                }
            }
            
            // Check usCities collection structure - just key fields
            logger.info("\n=== US Cities Collection Sample (key fields only) ===");
            MongoCollection<Document> usCities = database.getCollection("usCities");
            count = 0;
            for (Document city : usCities.find().limit(5)) {
                logger.info("City {}: city={}, state={}, _id={}", 
                    ++count,
                    city.getString("city"),
                    city.getString("state"),
                    city.get("_id"));
                
                // Check for any numeric ID fields
                for (String key : city.keySet()) {
                    Object value = city.get(key);
                    if (value instanceof Number) {
                        logger.info("  Numeric field found: {} = {}", key, value);
                    }
                }
            }
            
            // Check if there's a cities collection
            logger.info("\n=== Checking for city-related collections ===");
            for (String collectionName : database.listCollectionNames()) {
                if (collectionName.toLowerCase().contains("city") || collectionName.toLowerCase().contains("cities")) {
                    logger.info("Found collection: {}", collectionName);
                    // Check document count
                    long docCount = database.getCollection(collectionName).countDocuments();
                    logger.info("  Document count: {}", docCount);
                }
            }
            
            // Look for a specific numeric city ID in usCities if we found one in brokerages
            logger.info("\n=== Testing specific city ID lookup ===");
            Document sampleBrokerage = brokerages.find().first();
            if (sampleBrokerage != null) {
                Object cityValue = sampleBrokerage.get("city");
                if (cityValue instanceof Number) {
                    int cityId = ((Number) cityValue).intValue();
                    logger.info("Found numeric city ID {} in brokerage '{}'", cityId, sampleBrokerage.getString("name"));
                    
                    // Try to find this ID in usCities
                    Document cityDoc = usCities.find(new Document("_id", cityId)).first();
                    if (cityDoc != null) {
                        logger.info("  Found matching city by _id: {}", cityDoc.getString("city"));
                    } else {
                        logger.info("  No city found with _id={}", cityId);
                    }
                    
                    // Try other field names
                    for (String fieldName : new String[]{"city_id", "cityId", "id", "code"}) {
                        cityDoc = usCities.find(new Document(fieldName, cityId)).first();
                        if (cityDoc != null) {
                            logger.info("  Found matching city by {}: {}", fieldName, cityDoc.getString("city"));
                            break;
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error during investigation", e);
        } finally {
            mongoClient.close();
        }
        
        logger.info("\nInvestigation complete!");
    }
}