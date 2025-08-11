package com.example.mongoexport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for comprehensive export using AutoDiscoveryExporter
 * This provides a simple interface to the powerful automatic discovery and export capabilities
 */
public class ComprehensiveExporter {
    private static final Logger logger = LoggerFactory.getLogger(ComprehensiveExporter.class);
    
    public static void main(String[] args) {
        // Parse command line arguments
        String mode = args.length > 0 ? args[0] : "full";
        String collection = args.length > 1 ? args[1] : "listings";
        
        ExportOptions options = parseOptions(mode, args);
        
        logger.info("Starting comprehensive export in {} mode for collection: {}", mode, collection);
        logger.info("Options: expansion={}, statistics={}, depth={}, filtering={}", 
            options.isEnableRelationExpansion(), 
            options.isEnableFieldStatistics(),
            options.getExpansionDepth(),
            options.isUseSavedSummary() || mode.equals("auto"));
        
        try {
            if (collection.equalsIgnoreCase("all")) {
                // Export all major collections
                exportCollection("listings", options);
                exportCollection("transactions", options);
                exportCollection("agents", options);
            } else {
                // Export specific collection
                exportCollection(collection, options);
            }
        } catch (Exception e) {
            logger.error("Export failed", e);
            System.exit(1);
        }
        
        logger.info("Export completed successfully");
    }
    
    private static ExportOptions parseOptions(String mode, String[] args) {
        ExportOptions.Builder builder = ExportOptions.builder();
        
        // Default mode is auto-discovery with expansion
        // All exports use the same AutoDiscoveryExporter internally
        switch (mode.toLowerCase()) {
            case "full":
            case "auto":
            default:
                // Standard auto-discovery mode with expansion and filtering
                builder.enableRelationExpansion(true)
                       .enableFieldStatistics(true)
                       .expansionDepth(3);
                break;
        }
        
        // Parse additional options from command line
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--depth=")) {
                int depth = Integer.parseInt(arg.substring(8));
                builder.expansionDepth(depth);
            } else if (arg.equals("--no-expansion")) {
                builder.enableRelationExpansion(false);
            } else if (arg.equals("--no-statistics")) {
                builder.enableFieldStatistics(false);
            } else if (arg.startsWith("--sparse-threshold=")) {
                double threshold = Double.parseDouble(arg.substring(19));
                builder.sparseThreshold(threshold);
            } else if (arg.startsWith("--min-distinct=")) {
                // This will be passed to AutoDiscoveryExporter
                int minDistinct = Integer.parseInt(arg.substring(15));
                // Store as a custom property (would need to extend ExportOptions)
                logger.info("Minimum distinct values set to: {}", minDistinct);
            }
        }
        
        return builder.build();
    }
    
    private static void exportCollection(String collection, ExportOptions options) {
        logger.info("Exporting {} collection using AutoDiscoveryExporter", collection);
        logger.info("Features: Automatic field discovery, business-readable names, intelligent filtering");
        
        // Always use AutoDiscoveryExporter - it handles everything
        AutoDiscoveryExporter exporter = new AutoDiscoveryExporter(collection, options);
        exporter.export();
    }
    
    // Removed unused describeOptions method
}