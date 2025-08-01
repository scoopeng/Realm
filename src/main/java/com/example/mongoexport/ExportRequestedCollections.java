package com.example.mongoexport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class ExportRequestedCollections {
    private static final Logger logger = LoggerFactory.getLogger(ExportRequestedCollections.class);
    
    public static void main(String[] args) {
        logger.info("Starting export of requested collections to tab-delimited CSV files");
        
        ExportConfig config = new ExportConfig();
        logger.info("Environment: {} (PRODUCTION)", config.getCurrentEnvironment());
        logger.info("Export strategy: {} (comma-separated within fields)", config.getExportStrategy());
        logger.info("Output format: Tab-delimited CSV for MySQL bulk loading");
        
        MongoToCSVExporter exporter = new MongoToCSVExporter(config);
        
        try {
            
            // Export agentclients
            logger.info("\n=== Exporting agentclients collection ===");
            List<String> agentClientsMultiValue = Arrays.asList("agents", "realmData.lifestyleNames", "realmData.tagNames");
            List<String> agentClientsFields = Arrays.asList(
                "_id", "fullName", "lastNameFirst", "client", "agents", 
                "statusIntent", "archived", "updatedAt",
                "realmData.ownerAgent", "realmData.lifestyleNames", "realmData.tagNames", "realmData.maxPrice"
            );
            exporter.exportCollection("agentclients", agentClientsMultiValue, agentClientsFields);
            
            // Export agents
            logger.info("\n=== Exporting agents collection ===");
            List<String> agentsMultiValue = Arrays.asList("agentTypes", "brokerages", "realmData.tagNames");
            List<String> agentsFields = Arrays.asList(
                "_id", "fullName", "person", "privateURL", 
                "brokerages", "agentTypes", "subscription",
                "archived", "deleted", "updatedAt",
                "realmData.clientCount", "realmData.listingCount", "realmData.tagNames"
            );
            exporter.exportCollection("agents", agentsMultiValue, agentsFields);
            
            // Export properties
            logger.info("\n=== Exporting properties collection ===");
            List<String> propertiesMultiValue = Arrays.asList("schools", "taxes");
            List<String> propertiesFields = Arrays.asList(
                "_id", "fullAddress", "streetAddress", "city", "state", "zipcode", "county",
                "location.type", "location.coordinates",
                "schools", "taxes", "deleted"
            );
            exporter.exportCollection("properties", propertiesMultiValue, propertiesFields);
            
            // Export people
            logger.info("\n=== Exporting people collection ===");
            List<String> peopleMultiValue = Arrays.asList("emails", "phones", "languages", "externalData");
            List<String> peopleFields = Arrays.asList(
                "_id", "name.fullName", "name.firstName", "name.lastName", 
                "primaryEmail", "primaryPhone", "emails", "phones",
                "address.streetAddress", "address.city", "address.state", "address.postalCode",
                "languages", "deleted"
            );
            exporter.exportCollection("people", peopleMultiValue, peopleFields);
            
            logger.info("\n✅ All exports completed successfully!");
            logger.info("Files are located in: {}", config.getOutputDirectory());
            logger.info("Files are tab-delimited and ready for MySQL bulk loading");
            
        } catch (Exception e) {
            logger.error("Export failed", e);
            System.exit(1);
        } finally {
            exporter.close();
        }
    }
}