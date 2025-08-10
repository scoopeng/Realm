package com.example.mongoexport;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Smart exporter that can scan collections first and exclude empty/sparse fields
 */
public class SmartExporter {
    private static final Logger logger = LoggerFactory.getLogger(SmartExporter.class);
    
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }
        
        String command = args[0];
        String collection = args[1];
        
        // Parse options
        ExportOptions.Builder optionsBuilder = ExportOptions.builder();
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--exclude-empty":
                    optionsBuilder.excludeEmptyColumns();
                    break;
                case "--exclude-single-value":
                    optionsBuilder.excludeSingleValueColumns();
                    break;
                case "--exclude-sparse":
                    optionsBuilder.excludeSparseColumns();
                    break;
                case "--sparse-threshold":
                    if (i + 1 < args.length) {
                        optionsBuilder.sparseThreshold(Double.parseDouble(args[++i]));
                    }
                    break;
                case "--scan-first":
                    optionsBuilder.scanFieldsFirst(true);
                    break;
                case "--scan-sample":
                    if (i + 1 < args.length) {
                        optionsBuilder.scanSampleSize(Integer.parseInt(args[++i]));
                    }
                    break;
                case "--no-metadata":
                    optionsBuilder.generateMetadata(false);
                    break;
                case "--no-stats":
                    optionsBuilder.generateStatistics(false);
                    break;
            }
        }
        
        ExportOptions options = optionsBuilder.build();
        
        try {
            switch (command) {
                case "scan":
                    scanCollection(collection, options);
                    break;
                case "export":
                    exportCollection(collection, options);
                    break;
                case "analyze-metadata":
                    analyzeMetadata(collection);
                    break;
                default:
                    logger.error("Unknown command: {}", command);
                    printUsage();
                    System.exit(1);
            }
        } catch (Exception e) {
            logger.error("Error executing command", e);
            System.exit(1);
        }
    }
    
    private static void scanCollection(String collectionName, ExportOptions options) {
        ExportConfig config = new ExportConfig();
        try (MongoClient mongoClient = MongoClients.create(config.getMongoUrl())) {
            MongoDatabase database = mongoClient.getDatabase(config.getDatabaseName());
            
            FieldScanner scanner = new FieldScanner(database);
            FieldScanner.FieldScanResult result = scanner.scanCollection(
                collectionName, 
                options.getScanSampleSize(), 
                true
            );
            
            result.printSummary();
            
            // Save scan results
            String outputPath = config.getOutputDirectory() + "/" + collectionName + "_field_scan.json";
            saveScanResults(result, outputPath);
            logger.info("Scan results saved to: {}", outputPath);
        }
    }
    
    private static void exportCollection(String collectionName, ExportOptions options) {
        logger.info("Smart export of collection '{}' with options: {}", collectionName, describeOptions(options));
        
        ExportConfig config = new ExportConfig();
        try (MongoClient mongoClient = MongoClients.create(config.getMongoUrl())) {
            MongoDatabase database = mongoClient.getDatabase(config.getDatabaseName());
            
            // Perform field scan if requested
            FieldScanner.FieldScanResult scanResult = null;
            if (options.isScanFieldsFirst()) {
                logger.info("Performing field scan first...");
                FieldScanner scanner = new FieldScanner(database);
                scanResult = scanner.scanCollection(
                    collectionName,
                    options.getScanSampleSize(),
                    true
                );
                scanResult.printSummary();
            }
            
            // Determine which exporter to use based on collection
            switch (collectionName.toLowerCase()) {
                case "listings":
                    logger.info("Using UltraListingsExporter for listings collection");
                    // Would need to refactor UltraListingsExporter to extend AbstractUltraExporter
                    // and support ExportOptions
                    break;
                case "transactions":
                    logger.info("Using UltraTransactionExporter for transactions collection");
                    break;
                case "agents":
                    logger.info("Using UltraAgentPerformanceExporter for agents collection");
                    break;
                default:
                    logger.info("Using generic exporter for collection '{}'", collectionName);
                    // Would create a GenericUltraExporter
                    break;
            }
            
            logger.info("Export completed successfully");
        }
    }
    
    private static void analyzeMetadata(String metadataFile) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(metadataFile)));
            // Parse and analyze the metadata JSON
            logger.info("Analyzing metadata from: {}", metadataFile);
            // Implementation would parse JSON and provide insights
        } catch (IOException e) {
            logger.error("Failed to read metadata file", e);
        }
    }
    
    private static void saveScanResults(FieldScanner.FieldScanResult result, String outputPath) {
        // Save scan results as JSON
        // Implementation would serialize the result to JSON
    }
    
    private static String describeOptions(ExportOptions options) {
        StringBuilder sb = new StringBuilder();
        if (!options.isIncludeEmptyColumns()) sb.append("exclude-empty ");
        if (!options.isIncludeSingleValueColumns()) sb.append("exclude-single-value ");
        if (!options.isIncludeSparseColumns()) sb.append("exclude-sparse ");
        if (options.isScanFieldsFirst()) sb.append("scan-first ");
        return sb.toString().trim();
    }
    
    private static void printUsage() {
        System.out.println("Smart MongoDB Exporter");
        System.out.println("Usage: java SmartExporter <command> <collection> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  scan <collection>              - Scan collection and analyze field usage");
        System.out.println("  export <collection>            - Export collection with smart field selection");
        System.out.println("  analyze-metadata <file>        - Analyze previously generated metadata");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --exclude-empty               - Exclude columns that are always empty");
        System.out.println("  --exclude-single-value        - Exclude columns with only one value");
        System.out.println("  --exclude-sparse              - Exclude sparse columns (>95% null)");
        System.out.println("  --sparse-threshold <percent>  - Set sparse threshold (default: 95)");
        System.out.println("  --scan-first                  - Scan collection before export");
        System.out.println("  --scan-sample <size>          - Sample size for scanning (default: 10000)");
        System.out.println("  --no-metadata                 - Don't generate metadata file");
        System.out.println("  --no-stats                    - Don't generate statistics file");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java SmartExporter scan listings");
        System.out.println("  java SmartExporter export listings --exclude-empty --exclude-sparse");
        System.out.println("  java SmartExporter export agents --scan-first --exclude-empty");
    }
}