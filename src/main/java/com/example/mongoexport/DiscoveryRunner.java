package com.example.mongoexport;

import com.example.mongoexport.config.DiscoveryConfiguration;
import com.example.mongoexport.discovery.FieldDiscoveryService;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Main runner for the discovery phase.
 * This discovers all fields in a collection and saves the configuration to a JSON file.
 */
public class DiscoveryRunner {
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryRunner.class);
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: DiscoveryRunner <collection> [environment] [database]");
            System.err.println("Example: DiscoveryRunner listings");
            System.err.println("Example: DiscoveryRunner personwealthxdataMongo lake WealthX");
            System.exit(1);
        }

        String collectionName = args[0];
        String environmentOverride = args.length > 1 ? args[1] : null;
        String databaseOverride = args.length > 2 ? args[2] : null;

        try {
            // Load configuration with optional overrides
            ExportConfig config = new ExportConfig(environmentOverride, databaseOverride);
            String mongoUrl = config.getMongoUrl();
            String databaseName = config.getDatabaseName();
            
            logger.info("=== FIELD DISCOVERY PHASE ===");
            logger.info("Collection: {}", collectionName);
            logger.info("Database: {}", databaseName);
            
            // Connect to MongoDB
            try (MongoClient mongoClient = MongoClients.create(mongoUrl)) {
                MongoDatabase database = mongoClient.getDatabase(databaseName);
                
                // Run discovery
                FieldDiscoveryService discoveryService = new FieldDiscoveryService(database, collectionName);
                DiscoveryConfiguration discoveryConfig = discoveryService.discover();
                
                // Save configuration
                File configFile = DiscoveryConfiguration.getConfigFile(collectionName);
                configFile.getParentFile().mkdirs(); // Ensure config directory exists
                discoveryConfig.saveToFile(configFile);
                
                logger.info("\n=== DISCOVERY COMPLETE ===");
                logger.info("Configuration saved to: {}", configFile.getAbsolutePath());
                logger.info("Total fields discovered: {}", discoveryConfig.getFields().size());
                logger.info("Fields marked for inclusion: {}", discoveryConfig.getIncludedFields().size());
                logger.info("\nYou can now:");
                logger.info("1. Review and edit the configuration file if needed");
                logger.info("2. Run the export phase: ./gradlew configExport -Pcollection={}", collectionName);
            }
            
        } catch (Exception e) {
            logger.error("Discovery failed", e);
            System.exit(1);
        }
    }
}