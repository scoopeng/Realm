package com.example.mongoexport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args)
    {
        logger.info("MongoDB to CSV Exporter starting...");

        MongoToCSVExporter exporter = null;

        try
        {
            // Load configuration
            ExportConfig config = new ExportConfig();

            // Create exporter instance
            exporter = new MongoToCSVExporter(config);

            // Example 1: Export clients collection with interests as multi-value field
            exportClientsCollection(exporter);

            // Example 2: Export products collection with categories and tags as multi-value fields
            exportProductsCollection(exporter);

            // Example 3: Export users collection with roles as multi-value field
            exportUsersCollection(exporter);

            logger.info("All exports completed successfully!");

        } catch (Exception e)
        {
            logger.error("Export failed with error", e);
            System.exit(1);
        } finally
        {
            if (exporter != null)
            {
                exporter.close();
            }
        }
    }

    private static void exportClientsCollection(MongoToCSVExporter exporter)
    {
        logger.info("Starting export of clients collection...");

        List<String> multiValueFields = Arrays.asList("interests");
        List<String> fieldsToExport = Arrays.asList("_id", "name", "email", "age", "interests", "registrationDate", "status");

        exporter.exportCollection("clients", multiValueFields, fieldsToExport);
    }

    private static void exportProductsCollection(MongoToCSVExporter exporter)
    {
        logger.info("Starting export of products collection...");

        List<String> multiValueFields = Arrays.asList("categories", "tags");
        List<String> fieldsToExport = Arrays.asList("_id", "productName", "description", "price", "categories", "tags", "inStock", "createdAt");

        exporter.exportCollection("products", multiValueFields, fieldsToExport);
    }

    private static void exportUsersCollection(MongoToCSVExporter exporter)
    {
        logger.info("Starting export of users collection...");

        List<String> multiValueFields = Arrays.asList("roles");
        List<String> fieldsToExport = Arrays.asList("_id", "username", "email", "firstName", "lastName", "roles", "active", "lastLogin");

        exporter.exportCollection("users", multiValueFields, fieldsToExport);
    }
}