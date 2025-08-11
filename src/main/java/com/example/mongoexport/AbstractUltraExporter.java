package com.example.mongoexport;

import com.mongodb.client.*;
import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.Date;
import java.io.File;

/**
 * Abstract base class for ultra-comprehensive MongoDB to CSV exporters.
 * 
 * This class provides the common infrastructure for all exporters including:
 * - CSV writing with RFC 4180 compliant formatting (quote doubling for escaping)
 * - Field statistics collection and reporting
 * - Metadata generation in JSON format
 * - Progress tracking and performance metrics
 * - Configurable relation expansion and field filtering
 * 
 * Subclasses must implement:
 * - getCollectionName(): The MongoDB collection to export
 * - getExportFilePrefix(): Prefix for output files
 * - buildComprehensiveHeaders(): Column headers for the CSV
 * - loadCollection(): Load documents from MongoDB
 * - processDocument(): Convert a document to CSV row
 * 
 * CSV Format Details:
 * - Uses comma (,) as field separator
 * - Uses double quotes (") for quoting fields with special characters
 * - Escapes quotes by doubling them ("" represents a single quote)
 * - Uses CR+LF (\r\n) line endings per RFC 4180
 * 
 * @author MongoDB Export Team
 * @version 2.0
 */
public abstract class AbstractUltraExporter {
    protected static final Logger logger = LoggerFactory.getLogger(AbstractUltraExporter.class);
    protected static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    
    protected final ExportConfig config;
    protected final MongoDatabase database;
    protected final ExportOptions options;
    
    protected int totalRows = 0;
    
    // Relation expansion and field statistics
    protected RelationExpander relationExpander;
    protected FieldStatisticsCollector fieldStatisticsCollector;
    protected boolean enableRelationExpansion = false;
    protected boolean enableFieldStatistics = false;
    protected int expansionDepth = 2;
    
    public AbstractUltraExporter() {
        this(new ExportOptions());
    }
    
    public AbstractUltraExporter(ExportOptions options) {
        this.config = new ExportConfig();
        this.options = options != null ? options : new ExportOptions();
        MongoClient mongoClient = MongoClients.create(config.getMongoUrl());
        this.database = mongoClient.getDatabase(config.getDatabaseName());
        
        // Initialize based on options
        this.enableRelationExpansion = this.options.isEnableRelationExpansion();
        this.enableFieldStatistics = this.options.isEnableFieldStatistics();
        this.expansionDepth = this.options.getExpansionDepth();
        
        // Initialize components
        if (enableRelationExpansion) {
            this.relationExpander = new RelationExpander(database);
        }
    }
    
    /**
     * Main export method to be implemented by subclasses
     */
    public abstract void export();
    
    /**
     * Load collections into memory - to be implemented by subclasses
     */
    protected abstract void loadCollectionsIntoMemory();
    
    /**
     * Build headers for the export - to be implemented by subclasses
     */
    protected abstract String[] buildComprehensiveHeaders();
    
    /**
     * Get the export filename prefix
     */
    protected abstract String getExportFilePrefix();
    
    /**
     * Get the collection name for this exporter
     */
    protected abstract String getCollectionName();
    
    /**
     * Get the batch size for processing - FIXED: Made configurable
     */
    protected int getBatchSize() {
        // Check for environment variable or system property
        String batchSizeStr = System.getProperty("export.batch.size", 
            System.getenv("EXPORT_BATCH_SIZE"));
        if (batchSizeStr != null) {
            try {
                int size = Integer.parseInt(batchSizeStr);
                if (size > 0 && size <= 10000) {
                    return size;
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid batch size '{}', using default", batchSizeStr);
            }
        }
        return 2000; // Default
    }
    
    
    /**
     * Get fields to exclude based on saved summary or discovery report - FIXED: Unified approach
     */
    protected Set<String> getFieldsToExclude() {
        // Only process exclusions if explicitly requested
        if (!options.isUseSavedSummary()) {
            return new HashSet<>();
        }
        
        // Try both file types in priority order
        String[] possiblePaths = {
            config.getOutputDirectory() + "/" + getCollectionName() + "_discovery_report.json",
            config.getOutputDirectory() + "/" + getCollectionName() + "_summary.json"
        };
        
        for (String path : possiblePaths) {
            File file = new File(path);
            if (!file.exists()) continue;
            
            try {
                Set<String> toExclude;
                if (path.contains("_discovery_report")) {
                    toExclude = loadExclusionsFromDiscoveryReport(file);
                } else {
                    toExclude = loadExclusionsFromSummary(file);
                }
                
                logger.info("Loaded {} field exclusions from {}", toExclude.size(), file.getName());
                return toExclude;
                
            } catch (Exception e) {
                logger.warn("Could not load exclusions from {}: {}", file.getName(), e.getMessage());
            }
        }
        
        logger.info("No exclusion files found, including all fields");
        return new HashSet<>();
    }
    
    private Set<String> loadExclusionsFromDiscoveryReport(File file) throws IOException {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> report = mapper.readValue(file, Map.class);
        List<Map<String, Object>> fields = (List<Map<String, Object>>) report.get("fields");
        
        Set<String> toExclude = new HashSet<>();
        
        // Get actual sample size from report
        Integer sampleSize = (Integer) report.get("sampleSize");
        if (sampleSize == null) sampleSize = 1000; // fallback
        
        for (Map<String, Object> field : fields) {
            String fieldPath = (String) field.get("path");
            int occurrences = (Integer) field.get("occurrences");
            Integer uniqueValues = (Integer) field.get("uniqueValues");
            
            // Calculate null percentage
            double nullPercentage = ((sampleSize - occurrences) * 100.0) / sampleSize;
            
            // Apply consistent exclusion rules
            if (shouldExcludeField(occurrences, uniqueValues, nullPercentage)) {
                toExclude.add(fieldPath);
            }
        }
        
        return toExclude;
    }
    
    private Set<String> loadExclusionsFromSummary(File file) throws IOException {
        FieldStatisticsCollector.ExportSummary summary = FieldStatisticsCollector.loadSummary(file.getPath());
        Set<String> toExclude = new HashSet<>();
        
        for (FieldStatisticsCollector.FieldSummary field : summary.getFieldSummaries()) {
            // Use consistent exclusion logic
            if (field.getCategory() == FieldStatisticsCollector.FieldCategory.ALWAYS_EMPTY ||
                field.getCategory() == FieldStatisticsCollector.FieldCategory.SINGLE_VALUE ||
                (field.getCategory() == FieldStatisticsCollector.FieldCategory.SPARSE && 
                 field.getNullPercentage() > 95)) {
                toExclude.add(field.getFieldName());
            }
        }
        
        return toExclude;
    }
    
    private boolean shouldExcludeField(int occurrences, Integer uniqueValues, double nullPercentage) {
        // Consistent exclusion rules:
        // 1. Always empty (0 occurrences)
        // 2. Single value (only 1 unique value)
        // 3. Sparse (>95% null)
        return occurrences == 0 || 
               (uniqueValues != null && uniqueValues == 1) ||
               nullPercentage > 95;
    }
    
    /**
     * Common export logic with statistics tracking
     */
    protected void exportWithStatistics(ExportProcessor processor) {
        exportWithStatistics(processor, null);
    }
    
    protected void exportWithStatistics(ExportProcessor processor, ExportOptions options) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        // Add suffix to distinguish filtered exports from full exports
        String suffix = (options != null && options.isUseSavedSummary()) ? "_filtered" : "_full";
        String outputPath = config.getOutputDirectory() + "/" + getCollectionName() + suffix + "_" + timestamp + ".csv";
        
        try (FileWriter fileWriter = new FileWriter(outputPath); 
             CSVWriter csvWriter = (CSVWriter) new CSVWriterBuilder(fileWriter)
                 .withSeparator(',')
                 .withQuoteChar('"')
                 .withEscapeChar('\\')  // FIXED: OpenCSV uses backslash, quotes are auto-doubled
                 .withLineEnd("\r\n")  // RFC 4180: CR+LF line endings
                 .build()) {
            
            // Write headers
            String[] headers = buildComprehensiveHeaders();
            csvWriter.writeNext(headers);
            
            
            // Initialize field statistics collector if enabled
            if (enableFieldStatistics) {
                fieldStatisticsCollector = new FieldStatisticsCollector(getCollectionName());
                fieldStatisticsCollector.initializeFields(headers);
            }
            
            // Process data
            long startTime = System.currentTimeMillis();
            int processedCount = processor.process(csvWriter);
            
            // Synchronize totalRows with processedCount for statistics
            totalRows = processedCount;
            
            long totalTime = System.currentTimeMillis() - startTime;
            double totalSeconds = totalTime / 1000.0;
            
            // Set final metadata
            
            logger.info("Export completed: {} total rows written to {}", processedCount, outputPath);
            logger.info("Total time: {} seconds ({} rows/sec)", 
                String.format("%.1f", totalSeconds), String.format("%.1f", processedCount / totalSeconds));
            
            // Save consolidated summary (combines metadata + field statistics)
            if (enableFieldStatistics && fieldStatisticsCollector != null) {
                fieldStatisticsCollector.finalizeStatistics();
                String summaryPath = config.getOutputDirectory() + "/" + getCollectionName() + "_summary.json";
                try {
                    // Add export metadata to the summary
                    FieldStatisticsCollector.ExportSummary summary = fieldStatisticsCollector.generateSummary();
                    summary.setProcessingTimeMs((long)(totalSeconds * 1000));
                    fieldStatisticsCollector.saveSummary(summaryPath);
                    logger.info("Export summary saved to: {}", summaryPath);
                } catch (IOException e) {
                    logger.error("Failed to save export summary", e);
                }
            } else {
                // Even without field statistics, save basic metadata
                String summaryPath = config.getOutputDirectory() + "/" + getCollectionName() + "_summary.json";
                saveBasicSummary(summaryPath, processedCount, totalSeconds);
            }
            
            // Skip the redundant stats.txt and metadata.json files
            logger.debug("Skipping redundant stats.txt and metadata.json generation");
            
        } catch (IOException e) {
            logger.error("Failed to export data", e);
        }
    }
    
    
    // Helper method for collecting row statistics
    protected void collectRowStatistics(String[] row, String[] headers) {
        // Field statistics collection disabled in clean implementation
        // This is handled by ConfigurationBasedExporter directly
    }
    
    // Save basic summary when field statistics are not enabled
    private void saveBasicSummary(String path, int rowCount, double seconds) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            Map<String, Object> summary = new HashMap<>();
            summary.put("collection", getCollectionName());
            summary.put("rowCount", rowCount);
            summary.put("processingTimeSeconds", seconds);
            summary.put("timestamp", new Date());
            mapper.writeValue(new File(path), summary);
        } catch (IOException e) {
            logger.warn("Failed to save basic summary: {}", e.getMessage());
        }
    }
    
    // Functional interface for export processing
    protected interface ExportProcessor {
        int process(CSVWriter csvWriter) throws IOException;
    }
    
}
