package com.example.mongoexport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportFullDetail {
    private static final Logger logger = LoggerFactory.getLogger(ExportFullDetail.class);
    
    public static void main(String[] args) {
        logger.info("Starting FULL DETAIL export of all collections");
        logger.info("This will export ALL fields including nested objects and arrays");
        
        ExportConfig config = new ExportConfig();
        logger.info("Environment: {} (PRODUCTION)", config.getCurrentEnvironment());
        logger.info("Output format: Tab-delimited CSV with full document details");
        
        FullDetailExporter exporter = new FullDetailExporter(config);
        
        try {
            String[] collections = {"agentclients", "agents", "properties", "people"};
            
            for (String collection : collections) {
                logger.info("\n=== Exporting {} with FULL DETAIL ===", collection);
                exporter.exportCollectionWithFullDetail(collection);
            }
            
            logger.info("\n✅ All full detail exports completed successfully!");
            logger.info("Files are located in: {}", config.getOutputDirectory());
            logger.info("Files contain ALL fields from the MongoDB documents");
            
        } catch (Exception e) {
            logger.error("Export failed", e);
            System.exit(1);
        } finally {
            exporter.close();
        }
    }
}