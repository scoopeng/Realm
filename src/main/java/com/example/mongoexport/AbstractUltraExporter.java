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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

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
    
    // Column statistics tracking - FIXED: Using thread-safe collections
    protected Map<Integer, Map<String, Integer>> columnStats = new ConcurrentHashMap<>();
    protected Map<Integer, Integer> nullCounts = new ConcurrentHashMap<>();
    protected int totalRows = 0;
    
    // Export metadata
    protected ExportMetadata exportMetadata;
    
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
        this.exportMetadata = new ExportMetadata();
        
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
     * Process a document to extract row data
     */
    protected abstract String[] processDocument(Document doc);
    
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
            
            // Initialize column statistics and metadata
            initializeStatistics(headers);
            exportMetadata.setHeaders(Arrays.asList(headers));
            exportMetadata.setExportDate(new Date());
            exportMetadata.setExporterClass(this.getClass().getSimpleName());
            
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
            exportMetadata.setTotalRows(processedCount);
            exportMetadata.setProcessingTimeSeconds(totalSeconds);
            exportMetadata.setRowsPerSecond(processedCount / totalSeconds);
            
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
    
    /**
     * Initialize column statistics
     */
    protected void initializeStatistics(String[] headers) {
        for (int i = 0; i < headers.length; i++) {
            columnStats.put(i, new HashMap<>());
            nullCounts.put(i, 0);
            exportMetadata.addColumnMetadata(i, headers[i]);
        }
    }
    
    /**
     * Collect statistics for a row
     */
    protected void collectRowStatistics(String[] row, String[] headers) {
        for (int i = 0; i < row.length; i++) {
            String value = row[i];
            if (value == null || value.trim().isEmpty()) {
                nullCounts.put(i, nullCounts.getOrDefault(i, 0) + 1);
            } else {
                Map<String, Integer> valueMap = columnStats.get(i);
                valueMap.put(value, valueMap.getOrDefault(value, 0) + 1);
            }
        }
        
        // Also collect in field statistics collector if enabled
        if (enableFieldStatistics && fieldStatisticsCollector != null) {
            fieldStatisticsCollector.recordRow(row, headers);
        }
    }
    
    /**
     * Write column statistics file
     * @deprecated This method is not used by Enhanced exporters which use FieldStatisticsCollector instead.
     *             Kept for potential future use but currently not called.
     */
    @Deprecated
    protected void writeStatisticsFile(String statsPath, String[] headers) {
        try (FileWriter writer = new FileWriter(statsPath)) {
            writer.write("COLUMN STATISTICS REPORT\n");
            writer.write("========================\n");
            writer.write("Total rows analyzed: " + totalRows + "\n\n");
            
            List<ColumnStatistic> emptyColumns = new ArrayList<>();
            List<ColumnStatistic> singleValueColumns = new ArrayList<>();
            List<ColumnStatistic> sparseColumns = new ArrayList<>();
            
            for (int i = 0; i < headers.length; i++) {
                int nullCount = nullCounts.get(i);
                Map<String, Integer> valueMap = columnStats.get(i);
                double nullPercentage = (nullCount * 100.0) / totalRows;
                
                ColumnStatistic stat = new ColumnStatistic(i, headers[i], nullCount, nullPercentage, valueMap.size());
                
                // Update metadata
                exportMetadata.updateColumnMetadata(i, nullCount, valueMap.size(), 
                    valueMap.isEmpty() ? "" : valueMap.keySet().iterator().next());
                
                if (nullCount == totalRows) {
                    emptyColumns.add(stat);
                } else if (valueMap.size() == 1 && nullCount == 0) {
                    stat.setSingleValue(valueMap.keySet().iterator().next());
                    singleValueColumns.add(stat);
                } else if (nullPercentage > 95) {
                    sparseColumns.add(stat);
                }
            }
            
            // Write categorized columns
            writer.write("EMPTY COLUMNS (100% null/empty):\n");
            writer.write("---------------------------------\n");
            for (ColumnStatistic col : emptyColumns) {
                writer.write(String.format("Column %d: %s\n", col.index, col.name));
            }
            
            writer.write("\nSINGLE VALUE COLUMNS (only one non-null distinct value):\n");
            writer.write("--------------------------------------------------------\n");
            for (ColumnStatistic col : singleValueColumns) {
                writer.write(String.format("Column %d: %s = '%s'\n", col.index, col.name, col.singleValue));
            }
            
            writer.write("\nSPARSE COLUMNS (>95% null/empty):\n");
            writer.write("----------------------------------\n");
            for (ColumnStatistic col : sparseColumns) {
                writer.write(String.format("Column %d: %s (%.1f%% empty)\n", col.index, col.name, col.nullPercentage));
            }
            
            writer.write("\nDETAILED COLUMN STATISTICS:\n");
            writer.write("---------------------------\n");
            writeDetailedStatistics(writer, headers);
            
            logger.info("Statistics written to: {}", statsPath);
            
        } catch (IOException e) {
            logger.error("Failed to write statistics file", e);
        }
    }
    
    /**
     * Write detailed column statistics
     */
    private void writeDetailedStatistics(FileWriter writer, String[] headers) throws IOException {
        for (int i = 0; i < headers.length; i++) {
            writer.write(String.format("\nColumn %d: %s\n", i, headers[i]));
            writer.write(String.format("  Null/Empty count: %d (%.2f%%)\n", 
                nullCounts.get(i), (nullCounts.get(i) * 100.0) / totalRows));
            
            Map<String, Integer> valueMap = columnStats.get(i);
            writer.write(String.format("  Distinct values: %d\n", valueMap.size()));
            
            if (valueMap.size() > 0 && valueMap.size() <= 10) {
                writer.write("  Value distribution:\n");
                valueMap.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry -> {
                        try {
                            writer.write(String.format("    '%s': %d (%.2f%%)\n", 
                                entry.getKey().length() > 50 ? entry.getKey().substring(0, 50) + "..." : entry.getKey(),
                                entry.getValue(), 
                                (entry.getValue() * 100.0) / totalRows));
                        } catch (IOException e) {
                            logger.error("Error writing statistics", e);
                        }
                    });
            }
        }
    }
    
    /**
     * Write machine-readable metadata file
     * @deprecated This method is not used by Enhanced exporters which save summaries through FieldStatisticsCollector.
     *             Kept for potential future use but currently not called.
     */
    @Deprecated
    protected void writeMetadataFile(String metadataPath) {
        try (FileWriter writer = new FileWriter(metadataPath)) {
            writer.write(exportMetadata.toJson());
            logger.info("Metadata written to: {}", metadataPath);
        } catch (IOException e) {
            logger.error("Failed to write metadata file", e);
        }
    }
    
    /**
     * Safe string getter
     */
    protected String safeGetString(Document doc, String field) {
        if (doc == null) return "";
        Object value = doc.get(field);
        return value != null ? value.toString() : "";
    }
    
    /**
     * Safe double getter
     */
    protected Double safeGetDouble(Document doc, String field) {
        if (doc == null) return null;
        Object value = doc.get(field);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }
    
    /**
     * Safe string list getter
     */
    protected List<String> safeGetStringList(Document doc, String field) {
        if (doc == null) return new ArrayList<>();
        Object value = doc.get(field);
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return new ArrayList<>();
    }
    
    /**
     * Log progress during processing
     */
    protected void logProgress(int count, long startTime, String entityName) {
        if (count % 5000 == 0) {
            long currentTime = System.currentTimeMillis();
            double secondsElapsed = (currentTime - startTime) / 1000.0;
            double rate = count / secondsElapsed;
            logger.info("Processed {} {}... ({} {}/sec)", 
                count, entityName, String.format("%.1f", rate), entityName);
        }
    }
    
    /**
     * Save basic summary when field statistics are not enabled
     */
    private void saveBasicSummary(String path, int rowCount, double processingTime) {
        try (FileWriter writer = new FileWriter(path)) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("collectionName", getCollectionName());
            summary.put("exportDate", new Date());
            summary.put("totalDocuments", rowCount);
            summary.put("processingTimeSeconds", processingTime);
            summary.put("rowsPerSecond", rowCount / processingTime);
            summary.put("exporterClass", this.getClass().getSimpleName());
            
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(writer, summary);
            
            logger.info("Basic summary saved to: {}", path);
        } catch (IOException e) {
            logger.error("Failed to save basic summary", e);
        }
    }
    
    /**
     * Functional interface for export processing
     */
    @FunctionalInterface
    protected interface ExportProcessor {
        int process(CSVWriter csvWriter) throws IOException;
    }
    
    /**
     * Helper class for column statistics
     */
    private static class ColumnStatistic {
        final int index;
        final String name;
        final int nullCount;
        final double nullPercentage;
        final int distinctValues;
        String singleValue;
        
        ColumnStatistic(int index, String name, int nullCount, double nullPercentage, int distinctValues) {
            this.index = index;
            this.name = name;
            this.nullCount = nullCount;
            this.nullPercentage = nullPercentage;
            this.distinctValues = distinctValues;
        }
        
        void setSingleValue(String value) {
            this.singleValue = value;
        }
    }
}