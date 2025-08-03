package com.example.mongoexport;

import com.mongodb.client.*;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Test to check feature boolean fields in listings
 */
public class TestListingFeatures {
    private static final Logger logger = LoggerFactory.getLogger(TestListingFeatures.class);
    
    public static void main(String[] args) {
        ExportConfig config = new ExportConfig();
        MongoClient mongoClient = MongoClients.create(config.getMongoUrl());
        MongoDatabase database = mongoClient.getDatabase(config.getDatabaseName());
        
        try {
            logger.info("=== Analyzing feature boolean fields in listings ===");
            MongoCollection<Document> listings = database.getCollection("listings");
            
            // Features we're checking from the exporter
            String[] featureFields = {
                "hasPool", "hasSpa", "hasHotTub", "hasSauna", "hasFireplace",
                "hasBasement", "basementFinished", "hasAttic", "hasGarage", 
                "hasCarport", "hasRvParking", "hasBoatParking", "hasGuestHouse",
                "hasMotherInLaw", "hasWorkshop", "hasShed"
            };
            
            Map<String, Map<Object, Integer>> featureValueCounts = new HashMap<>();
            
            int docCount = 0;
            int docsWithFeatures = 0;
            
            for (Document listing : listings.find().limit(10000)) {
                docCount++;
                Document features = (Document) listing.get("features");
                if (features != null) {
                    docsWithFeatures++;
                    for (String feature : featureFields) {
                        Object value = features.get(feature);
                        featureValueCounts.computeIfAbsent(feature, k -> new HashMap<>())
                            .merge(value, 1, Integer::sum);
                    }
                }
            }
            
            logger.info("Analyzed {} documents, {} had features object", docCount, docsWithFeatures);
            
            // Report on each feature
            List<String> meaninglessFeatures = new ArrayList<>();
            
            for (String feature : featureFields) {
                Map<Object, Integer> valueCounts = featureValueCounts.get(feature);
                if (valueCounts != null) {
                    boolean hasTrue = valueCounts.containsKey(true);
                    boolean hasFalse = valueCounts.containsKey(false);
                    boolean hasNull = valueCounts.containsKey(null);
                    
                    logger.info("\nFeature '{}' distribution:", feature);
                    for (Map.Entry<Object, Integer> entry : valueCounts.entrySet()) {
                        Object value = entry.getKey();
                        logger.info("  {} : {} occurrences", 
                            value == null ? "null" : value, entry.getValue());
                    }
                    
                    // Check if meaningless (no true values)
                    if (!hasTrue && (hasFalse || hasNull)) {
                        meaninglessFeatures.add(feature);
                        logger.info("  -> MEANINGLESS: No true values found!");
                    }
                }
            }
            
            if (!meaninglessFeatures.isEmpty()) {
                logger.info("\n=== Summary of meaningless feature fields ===");
                logger.info("These features have no true values and should be removed:");
                for (String feature : meaninglessFeatures) {
                    logger.info("  - {}", feature);
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