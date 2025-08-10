package com.example.mongoexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Collects and analyzes field statistics during export operations
 */
public class FieldStatisticsCollector {
    private static final Logger logger = LoggerFactory.getLogger(FieldStatisticsCollector.class);
    
    private final Map<String, FieldStatistics> fieldStats = new ConcurrentHashMap<>();
    private final String collectionName;
    private long totalDocuments = 0;
    private long startTime;
    private long endTime;
    
    public FieldStatisticsCollector(String collectionName) {
        this.collectionName = collectionName;
        this.startTime = System.currentTimeMillis();
    }
    
    /**
     * Initialize field statistics for given headers
     */
    public void initializeFields(String[] headers) {
        for (String header : headers) {
            fieldStats.computeIfAbsent(header, k -> new FieldStatistics(k));
        }
    }
    
    /**
     * Record values for a data row
     */
    public void recordRow(String[] values, String[] headers) {
        totalDocuments++;
        
        for (int i = 0; i < headers.length && i < values.length; i++) {
            String header = headers[i];
            String value = values[i];
            FieldStatistics stats = fieldStats.get(header);
            
            if (stats != null) {
                stats.recordValue(value);
            }
        }
    }
    
    /**
     * Finalize statistics collection
     */
    public void finalizeStatistics() {
        this.endTime = System.currentTimeMillis();
        
        // Calculate final statistics for each field
        for (FieldStatistics stats : fieldStats.values()) {
            stats.calculateFinalStatistics(totalDocuments);
        }
    }
    
    /**
     * Get fields that should be excluded based on statistics
     */
    public Set<String> getFieldsToExclude(boolean excludeEmpty, boolean excludeSingleValue, boolean excludeSparse, double sparseThreshold) {
        Set<String> toExclude = new HashSet<>();
        
        for (FieldStatistics stats : fieldStats.values()) {
            if (excludeEmpty && stats.isAlwaysEmpty()) {
                toExclude.add(stats.fieldName);
            } else if (excludeSingleValue && stats.isSingleValue()) {
                toExclude.add(stats.fieldName);
            } else if (excludeSparse && stats.isSparse(sparseThreshold)) {
                toExclude.add(stats.fieldName);
            }
        }
        
        return toExclude;
    }
    
    /**
     * Generate a summary report
     */
    public ExportSummary generateSummary() {
        ExportSummary summary = new ExportSummary();
        summary.setCollectionName(collectionName);
        summary.setTotalDocuments(totalDocuments);
        summary.setExportDate(new Date());
        summary.setProcessingTimeMs(endTime - startTime);
        
        // Categorize fields
        List<FieldSummary> fieldSummaries = new ArrayList<>();
        for (FieldStatistics stats : fieldStats.values()) {
            FieldSummary fieldSummary = new FieldSummary();
            fieldSummary.setFieldName(stats.fieldName);
            fieldSummary.setNullCount(stats.nullCount);
            fieldSummary.setNonNullCount(stats.nonNullCount);
            fieldSummary.setUniqueValues(stats.uniqueValues.size());
            fieldSummary.setNullPercentage(stats.nullPercentage);
            fieldSummary.setSampleValues(stats.getSampleValues());
            fieldSummary.setCategory(stats.getCategory());
            fieldSummary.setDataType(stats.inferDataType());
            
            // Add value distribution for fields with few unique values
            if (stats.uniqueValues.size() <= 10 && stats.uniqueValues.size() > 0) {
                fieldSummary.setValueDistribution(stats.getValueDistribution());
            }
            
            fieldSummaries.add(fieldSummary);
        }
        
        // Sort by category and then by field name
        fieldSummaries.sort((a, b) -> {
            int categoryCompare = a.getCategory().compareTo(b.getCategory());
            if (categoryCompare != 0) return categoryCompare;
            return a.getFieldName().compareTo(b.getFieldName());
        });
        
        summary.setFieldSummaries(fieldSummaries);
        
        // Add statistics
        summary.setEmptyFieldCount(fieldSummaries.stream()
            .filter(f -> f.getCategory() == FieldCategory.ALWAYS_EMPTY).count());
        summary.setSingleValueFieldCount(fieldSummaries.stream()
            .filter(f -> f.getCategory() == FieldCategory.SINGLE_VALUE).count());
        summary.setSparseFieldCount(fieldSummaries.stream()
            .filter(f -> f.getCategory() == FieldCategory.SPARSE).count());
        summary.setMeaningfulFieldCount(fieldSummaries.stream()
            .filter(f -> f.getCategory() == FieldCategory.MEANINGFUL).count());
        
        return summary;
    }
    
    /**
     * Save summary to JSON file
     */
    public void saveSummary(String outputPath) throws IOException {
        ExportSummary summary = generateSummary();
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(outputPath), summary);
        
        logger.info("Saved export summary to: {}", outputPath);
    }
    
    /**
     * Load summary from JSON file
     */
    public static ExportSummary loadSummary(String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new File(path), ExportSummary.class);
    }
    
    /**
     * Inner class for tracking statistics of a single field
     */
    private static class FieldStatistics {
        final String fieldName;
        long nullCount = 0;
        long nonNullCount = 0;
        final Set<String> uniqueValues = new HashSet<>();
        final Map<String, Long> valueCounts = new HashMap<>();
        final List<String> sampleValues = new ArrayList<>();
        double nullPercentage = 0.0;
        
        // Limits for memory management
        private static final int MAX_UNIQUE_VALUES = 1000;
        private static final int MAX_SAMPLE_VALUES = 10;
        
        public FieldStatistics(String fieldName) {
            this.fieldName = fieldName;
        }
        
        public void recordValue(String value) {
            if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) {
                nullCount++;
            } else {
                nonNullCount++;
                
                // Track unique values (up to a limit)
                if (uniqueValues.size() < MAX_UNIQUE_VALUES) {
                    uniqueValues.add(value);
                }
                
                // Track value counts for distribution
                if (uniqueValues.size() <= 100) {
                    valueCounts.merge(value, 1L, Long::sum);
                }
                
                // Collect sample values
                if (sampleValues.size() < MAX_SAMPLE_VALUES && !sampleValues.contains(value)) {
                    sampleValues.add(value);
                }
            }
        }
        
        public void calculateFinalStatistics(long totalDocuments) {
            if (totalDocuments > 0) {
                nullPercentage = (double) nullCount / totalDocuments * 100;
            }
        }
        
        public boolean isAlwaysEmpty() {
            return nonNullCount == 0;
        }
        
        public boolean isSingleValue() {
            return uniqueValues.size() == 1;
        }
        
        public boolean isSparse(double threshold) {
            return nullPercentage >= threshold;
        }
        
        public FieldCategory getCategory() {
            if (isAlwaysEmpty()) {
                return FieldCategory.ALWAYS_EMPTY;
            } else if (isSingleValue()) {
                return FieldCategory.SINGLE_VALUE;
            } else if (isSparse(95.0)) {
                return FieldCategory.SPARSE;
            } else {
                return FieldCategory.MEANINGFUL;
            }
        }
        
        public List<String> getSampleValues() {
            return new ArrayList<>(sampleValues);
        }
        
        public Map<String, Long> getValueDistribution() {
            return new HashMap<>(valueCounts);
        }
        
        public String inferDataType() {
            if (uniqueValues.isEmpty()) {
                return "unknown";
            }
            
            // Sample some values to infer type
            boolean allNumbers = true;
            boolean allDates = true;
            boolean allBooleans = true;
            
            for (String value : uniqueValues) {
                if (value == null) continue;
                
                // Check if number
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    allNumbers = false;
                }
                
                // Check if boolean
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    allBooleans = false;
                }
                
                // Check if date (simple check)
                if (!value.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                    allDates = false;
                }
                
                if (!allNumbers && !allDates && !allBooleans) {
                    break;
                }
            }
            
            if (allNumbers) return "number";
            if (allBooleans) return "boolean";
            if (allDates) return "date";
            return "string";
        }
    }
    
    /**
     * Export summary data structure
     */
    public static class ExportSummary {
        private String collectionName;
        private long totalDocuments;
        private Date exportDate;
        private long processingTimeMs;
        private List<FieldSummary> fieldSummaries;
        private long emptyFieldCount;
        private long singleValueFieldCount;
        private long sparseFieldCount;
        private long meaningfulFieldCount;
        
        // Getters and setters
        public String getCollectionName() { return collectionName; }
        public void setCollectionName(String collectionName) { this.collectionName = collectionName; }
        
        public long getTotalDocuments() { return totalDocuments; }
        public void setTotalDocuments(long totalDocuments) { this.totalDocuments = totalDocuments; }
        
        public Date getExportDate() { return exportDate; }
        public void setExportDate(Date exportDate) { this.exportDate = exportDate; }
        
        public long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
        
        public List<FieldSummary> getFieldSummaries() { return fieldSummaries; }
        public void setFieldSummaries(List<FieldSummary> fieldSummaries) { this.fieldSummaries = fieldSummaries; }
        
        public long getEmptyFieldCount() { return emptyFieldCount; }
        public void setEmptyFieldCount(long emptyFieldCount) { this.emptyFieldCount = emptyFieldCount; }
        
        public long getSingleValueFieldCount() { return singleValueFieldCount; }
        public void setSingleValueFieldCount(long singleValueFieldCount) { this.singleValueFieldCount = singleValueFieldCount; }
        
        public long getSparseFieldCount() { return sparseFieldCount; }
        public void setSparseFieldCount(long sparseFieldCount) { this.sparseFieldCount = sparseFieldCount; }
        
        public long getMeaningfulFieldCount() { return meaningfulFieldCount; }
        public void setMeaningfulFieldCount(long meaningfulFieldCount) { this.meaningfulFieldCount = meaningfulFieldCount; }
    }
    
    /**
     * Field summary data structure
     */
    public static class FieldSummary {
        private String fieldName;
        private long nullCount;
        private long nonNullCount;
        private int uniqueValues;
        private double nullPercentage;
        private List<String> sampleValues;
        private FieldCategory category;
        private String dataType;
        private Map<String, Long> valueDistribution;
        
        // Getters and setters
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        
        public long getNullCount() { return nullCount; }
        public void setNullCount(long nullCount) { this.nullCount = nullCount; }
        
        public long getNonNullCount() { return nonNullCount; }
        public void setNonNullCount(long nonNullCount) { this.nonNullCount = nonNullCount; }
        
        public int getUniqueValues() { return uniqueValues; }
        public void setUniqueValues(int uniqueValues) { this.uniqueValues = uniqueValues; }
        
        public double getNullPercentage() { return nullPercentage; }
        public void setNullPercentage(double nullPercentage) { this.nullPercentage = nullPercentage; }
        
        public List<String> getSampleValues() { return sampleValues; }
        public void setSampleValues(List<String> sampleValues) { this.sampleValues = sampleValues; }
        
        public FieldCategory getCategory() { return category; }
        public void setCategory(FieldCategory category) { this.category = category; }
        
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        
        public Map<String, Long> getValueDistribution() { return valueDistribution; }
        public void setValueDistribution(Map<String, Long> valueDistribution) { this.valueDistribution = valueDistribution; }
    }
    
    /**
     * Field category enumeration
     */
    public enum FieldCategory {
        ALWAYS_EMPTY,   // Field is always null/empty
        SINGLE_VALUE,   // Field has only one unique value
        SPARSE,         // Field is mostly null (>95% by default)
        MEANINGFUL      // Field has meaningful variation
    }
}