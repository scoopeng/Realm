package com.example.mongoexport;

import com.mongodb.client.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test to specifically investigate brokerage city field values
 */
public class TestBrokerageCityField {
    private static final Logger logger = LoggerFactory.getLogger(TestBrokerageCityField.class);
    
    public static void main(String[] args) {
        ExportConfig config = new ExportConfig();
        MongoClient mongoClient = MongoClients.create(config.getMongoUrl());
        MongoDatabase database = mongoClient.getDatabase(config.getDatabaseName());
        
        try {
            // Check brokerages with non-null city fields
            logger.info("=== Looking for brokerages with city values ===");
            MongoCollection<Document> brokerages = database.getCollection("brokerages");
            
            // Find brokerages where city is not null
            Document query = new Document("city", new Document("$ne", null));
            int count = 0;
            for (Document brokerage : brokerages.find(query).limit(10)) {
                Object cityValue = brokerage.get("city");
                logger.info("Brokerage: {} | City value: {} (type: {})", 
                    brokerage.getString("name"),
                    cityValue,
                    cityValue != null ? cityValue.getClass().getSimpleName() : "null");
                count++;
            }
            
            if (count == 0) {
                logger.info("No brokerages found with non-null city values");
                
                // Let's check all fields in a sample brokerage
                logger.info("\n=== Sample brokerage document structure ===");
                Document sampleBrokerage = brokerages.find().first();
                if (sampleBrokerage != null) {
                    for (String key : sampleBrokerage.keySet()) {
                        Object value = sampleBrokerage.get(key);
                        String valueStr = value != null ? value.toString() : "null";
                        if (valueStr.length() > 50) {
                            valueStr = valueStr.substring(0, 47) + "...";
                        }
                        logger.info("  {}: {} ({})", key, valueStr, value != null ? value.getClass().getSimpleName() : "null");
                    }
                }
            }
            
            // Now let's trace through how listings connect to brokerages
            logger.info("\n=== Tracing listing -> agent -> brokerage connection ===");
            MongoCollection<Document> listings = database.getCollection("listings");
            MongoCollection<Document> currentAgents = database.getCollection("currentAgents");
            
            // Get a sample listing
            Document listing = listings.find().first();
            if (listing != null) {
                logger.info("Sample listing ID: {}", listing.getObjectId("_id"));
                
                // Get the currentAgentId
                Object currentAgentId = listing.get("currentAgentId");
                logger.info("  currentAgentId: {} ({})", currentAgentId, 
                    currentAgentId != null ? currentAgentId.getClass().getSimpleName() : "null");
                
                if (currentAgentId instanceof ObjectId) {
                    // Look up the current agent
                    Document agent = currentAgents.find(new Document("_id", currentAgentId)).first();
                    if (agent != null) {
                        logger.info("  Found currentAgent: {}", agent.getString("name"));
                        
                        // Check brokerage info in the agent
                        Object brokerageData = agent.get("brokerageData");
                        if (brokerageData instanceof Document) {
                            Document brokerageDoc = (Document) brokerageData;
                            logger.info("  Agent has brokerageData:");
                            for (String key : brokerageDoc.keySet()) {
                                Object value = brokerageDoc.get(key);
                                String valueStr = value != null ? value.toString() : "null";
                                if (valueStr.length() > 50) {
                                    valueStr = valueStr.substring(0, 47) + "...";
                                }
                                logger.info("    {}: {}", key, valueStr);
                            }
                        }
                        
                        // Check if there's a direct brokerage reference
                        Object brokerageId = agent.get("brokerage");
                        if (brokerageId != null) {
                            logger.info("  Agent has brokerage ID: {} ({})", brokerageId, brokerageId.getClass().getSimpleName());
                        }
                    } else {
                        logger.info("  CurrentAgent not found!");
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