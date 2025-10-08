package com.example.mongoexport;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Standalone MongoDB explorer for investigating database contents.
 * Does not modify any existing code or workflows.
 */
public class MongoExplorerRunner {
    private static final Logger logger = LoggerFactory.getLogger(MongoExplorerRunner.class);
    private static final int SAMPLE_SIZE = 100;

    public static void main(String[] args) {
        String environment = args.length > 0 ? args[0] : "lake";
        String targetCollection = args.length > 1 && !args[1].equals("null") ? args[1] : null;
        String databaseOverride = args.length > 2 ? args[2] : null;

        try {
            ExportConfig config = new ExportConfig();

            // Get MongoDB URL for specified environment
            String mongoUrl = getMongoUrlForEnvironment(environment);
            String databaseName = databaseOverride != null ? databaseOverride : config.getDatabaseName();

            logger.info("=== MONGODB EXPLORER ===");
            logger.info("Environment: {}", environment);
            logger.info("Database: {}", databaseName);
            logger.info("");

            try (MongoClient mongoClient = MongoClients.create(mongoUrl)) {
                // First, show available databases
                logger.info("Available databases:");
                for (String dbName : mongoClient.listDatabaseNames()) {
                    logger.info("  - {}", dbName);
                }
                logger.info("");

                MongoDatabase database = mongoClient.getDatabase(databaseName);

                if (targetCollection == null) {
                    // Mode 1: List all collections
                    listCollections(database);
                } else {
                    // Mode 2: Inspect specific collection
                    inspectCollection(database, targetCollection);
                }
            }

        } catch (Exception e) {
            logger.error("Exploration failed", e);
            System.exit(1);
        }
    }

    private static String getMongoUrlForEnvironment(String env) {
        // Read from application.properties based on environment
        com.typesafe.config.Config config = com.typesafe.config.ConfigFactory.load();
        String urlKey = String.format("mongodb.url.%s", env);
        return config.getString(urlKey);
    }

    private static void listCollections(MongoDatabase database) {
        logger.info("Collections in database:");
        logger.info("─".repeat(70));
        logger.info(String.format("%-40s %20s", "Collection Name", "Document Count"));
        logger.info("─".repeat(70));

        List<String> collections = new ArrayList<>();
        database.listCollectionNames().into(collections);
        Collections.sort(collections);

        for (String collectionName : collections) {
            try {
                MongoCollection<Document> collection = database.getCollection(collectionName);
                long count = collection.countDocuments();
                logger.info(String.format("%-40s %,20d", collectionName, count));
            } catch (Exception e) {
                logger.info(String.format("%-40s %20s", collectionName, "Error counting"));
            }
        }

        logger.info("─".repeat(70));
        logger.info("Total collections: {}", collections.size());
        logger.info("\nTo inspect a collection: ./gradlew explore -Pcollection=<name>");
    }

    private static void inspectCollection(MongoDatabase database, String collectionName) {
        logger.info("Inspecting collection: {}", collectionName);
        logger.info("─".repeat(80));

        MongoCollection<Document> collection = database.getCollection(collectionName);

        // Basic stats
        long totalCount = collection.countDocuments();
        logger.info("Total documents: {}", String.format("%,d", totalCount));
        logger.info("");

        // Sample documents and discover fields
        logger.info("Sampling {} documents to discover fields...", SAMPLE_SIZE);
        Map<String, FieldInfo> fields = new TreeMap<>();

        int sampled = 0;
        for (Document doc : collection.find().limit(SAMPLE_SIZE)) {
            discoverFields(doc, "", fields);
            sampled++;
        }

        logger.info("Sampled {} documents", sampled);
        logger.info("");

        // Display fields
        logger.info("Discovered fields:");
        logger.info("─".repeat(80));
        logger.info(String.format("%-50s %15s %10s", "Field Path", "Type(s)", "Occurrences"));
        logger.info("─".repeat(80));

        for (Map.Entry<String, FieldInfo> entry : fields.entrySet()) {
            FieldInfo info = entry.getValue();
            String types = String.join(", ", info.types);
            logger.info(String.format("%-50s %15s %10d",
                entry.getKey(), types, info.count));
        }

        logger.info("─".repeat(80));
        logger.info("\nTotal unique field paths: {}", fields.size());

        // Show sample document
        logger.info("");
        logger.info("Sample document:");
        logger.info("─".repeat(80));
        Document sample = collection.find().first();
        if (sample != null) {
            logger.info(sample.toJson(
                org.bson.json.JsonWriterSettings.builder()
                    .indent(true)
                    .build()
            ));
        }
        logger.info("─".repeat(80));
    }

    private static void discoverFields(Document doc, String prefix, Map<String, FieldInfo> fields) {
        for (String key : doc.keySet()) {
            String fieldPath = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = doc.get(key);

            if (value == null) {
                continue;
            }

            String type = getSimpleType(value);
            fields.computeIfAbsent(fieldPath, k -> new FieldInfo()).addOccurrence(type);

            // Recurse into nested documents
            if (value instanceof Document) {
                discoverFields((Document) value, fieldPath, fields);
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                if (!list.isEmpty() && list.get(0) instanceof Document) {
                    discoverFields((Document) list.get(0), fieldPath + "[]", fields);
                }
            }
        }
    }

    private static String getSimpleType(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "String";
        if (value instanceof Integer) return "Integer";
        if (value instanceof Long) return "Long";
        if (value instanceof Double) return "Double";
        if (value instanceof Boolean) return "Boolean";
        if (value instanceof Date) return "Date";
        if (value instanceof org.bson.types.ObjectId) return "ObjectId";
        if (value instanceof Document) return "Document";
        if (value instanceof List) return "Array";
        return value.getClass().getSimpleName();
    }

    static class FieldInfo {
        Set<String> types = new TreeSet<>();
        int count = 0;

        void addOccurrence(String type) {
            types.add(type);
            count++;
        }
    }
}
