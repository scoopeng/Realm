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
        
        switch (mode.toLowerCase()) {
            case "full":
                // Full export with all data and relation expansion
                builder.enableRelationExpansion(true)
                       .enableFieldStatistics(true)
                       .expansionDepth(2)
                       .generateMetadata(true)
                       .generateStatistics(true);
                break;
                
            case "auto":
                // Automatic mode - discovers fields, filters by distinct values, uses business names
                // This is the recommended mode for most use cases
                builder.enableRelationExpansion(false)  // Disabled for initial discovery
                       .enableFieldStatistics(true)
                       .generateMetadata(true)
                       .generateStatistics(true)
                       .excludeEmptyColumns()
                       .excludeSingleValueColumns()
                       .excludeSparseColumns();
                break;
                
            case "analyze":
                // Analyze mode - collect statistics without expansion
                builder.enableRelationExpansion(false)
                       .enableFieldStatistics(true)
                       .generateMetadata(true)
                       .generateStatistics(true);
                break;
                
            case "filtered":
                // Filtered mode - uses intelligent filtering based on distinct values
                builder.enableRelationExpansion(false)  // Disabled for speed
                       .enableFieldStatistics(false)
                       .excludeEmptyColumns()
                       .excludeSingleValueColumns()
                       .excludeSparseColumns()
                       .sparseThreshold(95.0);
                break;
                
            case "minimal":
                // Minimal mode - no expansion, no stats, just core data
                builder.enableRelationExpansion(false)
                       .enableFieldStatistics(false)
                       .generateMetadata(false)
                       .generateStatistics(false);
                break;
                
            default:
                logger.warn("Unknown mode: {}. Using auto mode.", mode);
                return parseOptions("auto", args);
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
    
    private static String describeOptions(ExportOptions options) {
        StringBuilder desc = new StringBuilder();
        desc.append("expansion=").append(options.isEnableRelationExpansion());
        desc.append(", statistics=").append(options.isEnableFieldStatistics());
        desc.append(", depth=").append(options.getExpansionDepth());
        if (!options.isIncludeEmptyColumns()) desc.append(", excludeEmpty");
        if (!options.isIncludeSingleValueColumns()) desc.append(", excludeSingleValue");
        if (!options.isIncludeSparseColumns()) desc.append(", excludeSparse");
        return desc.toString();
    }
}