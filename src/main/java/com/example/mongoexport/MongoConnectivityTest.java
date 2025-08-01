package com.example.mongoexport;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MongoConnectivityTest {
    private static final Logger logger = LoggerFactory.getLogger(MongoConnectivityTest.class);
    
    public static void main(String[] args) {
        logger.info("Starting MongoDB connectivity test for DEV environment");
        
        try {
            ExportConfig config = new ExportConfig();
            String mongoUrl = config.getMongoUrl();
            String databaseName = config.getDatabaseName();
            
            logger.info("Connecting to MongoDB...");
            logger.info("Environment: {}", config.getCurrentEnvironment());
            logger.info("Database: {}", databaseName);
            
            // Parse connection string to extract host
            ConnectionString connectionString = new ConnectionString(mongoUrl);
            String host = connectionString.getHosts().get(0).split(":")[0];
            logger.info("MongoDB Host: {}", host);
            logger.info("Connection String Type: {}", mongoUrl.startsWith("mongodb+srv") ? "SRV (MongoDB Atlas)" : "Standard");
            
            // For mongodb+srv connections, the driver handles SRV lookup automatically
            if (mongoUrl.startsWith("mongodb+srv")) {
                logger.info("\n=== MongoDB Atlas SRV Connection ===");
                logger.info("The MongoDB driver will automatically discover cluster topology using SRV records");
                logger.info("Skipping manual DNS resolution test for SRV connections");
            } else {
                // Test DNS resolution for standard connections
                logger.info("\n=== Testing DNS resolution ===");
                try {
                    InetAddress[] addresses = InetAddress.getAllByName(host);
                    logger.info("DNS resolution successful for {}", host);
                    for (InetAddress addr : addresses) {
                        logger.info("  - Resolved to: {}", addr.getHostAddress());
                    }
                } catch (UnknownHostException e) {
                    logger.error("❌ DNS resolution failed for {}: {}", host, e.getMessage());
                    logger.error("\nPossible causes:");
                    logger.error("  1. The hostname is incorrect");
                    logger.error("  2. Network/DNS configuration issues");
                    logger.error("  3. The MongoDB host is not accessible from this network");
                    logger.error("\nPlease verify the MongoDB connection string in application.properties");
                    System.exit(1);
                }
            }
            
            logger.info("\n=== Attempting MongoDB connection ===");
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .applyToSocketSettings(builder -> 
                        builder.connectTimeout(10, TimeUnit.SECONDS)
                               .readTimeout(10, TimeUnit.SECONDS))
                    .applyToClusterSettings(builder ->
                        builder.serverSelectionTimeout(10, TimeUnit.SECONDS))
                    .build();
            
            try (MongoClient mongoClient = MongoClients.create(settings)) {
                logger.info("✅ Successfully connected to MongoDB!");
                
                MongoDatabase database = mongoClient.getDatabase(databaseName);
                
                logger.info("\n=== Listing all collections in database '{}' ===", databaseName);
                List<String> collections = new ArrayList<>();
                for (String collection : database.listCollectionNames()) {
                    collections.add(collection);
                    logger.info("  - {}", collection);
                }
                
                logger.info("\nTotal collections found: {}", collections.size());
                
                // Test if we can access a specific collection
                if (!collections.isEmpty()) {
                    String firstCollection = collections.get(0);
                    long count = database.getCollection(firstCollection).countDocuments();
                    logger.info("\nTest query - Document count in '{}': {}", firstCollection, count);
                }
                
                logger.info("\n✅ MongoDB connectivity test PASSED!");
                logger.info("\nSummary:");
                logger.info("  - Environment: {}", config.getCurrentEnvironment());
                logger.info("  - Host: {}", host);
                logger.info("  - Database: {}", databaseName);
                logger.info("  - Collections found: {}", collections.size());
                
            } catch (Exception e) {
                logger.error("❌ Failed to connect to MongoDB: {}", e.getMessage());
                if (e.getMessage().contains("Authentication failed")) {
                    logger.error("\nAuthentication failed. Please check:");
                    logger.error("  - Username and password in application.properties");
                    logger.error("  - Authentication database (authSource)");
                } else if (e.getMessage().contains("Timeout")) {
                    logger.error("\nConnection timeout. Please check:");
                    logger.error("  - MongoDB server is running and accessible");
                    logger.error("  - Firewall/network configuration");
                    logger.error("  - Port 27017 is open");
                }
                System.exit(1);
            }
            
        } catch (Exception e) {
            logger.error("Failed to load configuration: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}