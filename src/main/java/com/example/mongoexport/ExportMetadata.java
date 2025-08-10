package com.example.mongoexport;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.*;

/**
 * Machine-readable metadata for export files
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExportMetadata {
    private String exporterClass;
    private Date exportDate;
    private int totalRows;
    private double processingTimeSeconds;
    private double rowsPerSecond;
    private List<String> headers;
    private Map<Integer, ColumnMetadata> columns = new HashMap<>();
    private Map<String, Object> exportOptions = new HashMap<>();
    private DatabaseInfo databaseInfo = new DatabaseInfo();
    
    // Summary statistics
    private int emptyColumnCount = 0;
    private int singleValueColumnCount = 0;
    private int sparseColumnCount = 0;
    private int meaningfulColumnCount = 0;
    
    public void addColumnMetadata(int index, String name) {
        columns.put(index, new ColumnMetadata(index, name));
    }
    
    public void updateColumnMetadata(int index, int nullCount, int distinctValues, String sampleValue) {
        ColumnMetadata col = columns.get(index);
        if (col != null) {
            col.setNullCount(nullCount);
            col.setDistinctValues(distinctValues);
            col.setSampleValue(sampleValue);
            col.setNullPercentage((nullCount * 100.0) / totalRows);
            
            // Categorize column
            if (nullCount == totalRows) {
                col.setCategory("empty");
                emptyColumnCount++;
            } else if (distinctValues == 1 && nullCount == 0) {
                col.setCategory("single_value");
                singleValueColumnCount++;
            } else if (col.getNullPercentage() > 95) {
                col.setCategory("sparse");
                sparseColumnCount++;
            } else {
                col.setCategory("meaningful");
                meaningfulColumnCount++;
            }
        }
    }
    
    public void setDatabaseInfo(String name, String environment, long collectionSize) {
        databaseInfo.setName(name);
        databaseInfo.setEnvironment(environment);
        databaseInfo.setCollectionSize(collectionSize);
    }
    
    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    // Column metadata inner class
    public static class ColumnMetadata {
        private int index;
        private String name;
        private String category;
        private int nullCount;
        private double nullPercentage;
        private int distinctValues;
        private String sampleValue;
        private String dataType;
        
        public ColumnMetadata() {}
        
        public ColumnMetadata(int index, String name) {
            this.index = index;
            this.name = name;
        }
        
        // Getters and setters
        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public int getNullCount() { return nullCount; }
        public void setNullCount(int nullCount) { this.nullCount = nullCount; }
        
        public double getNullPercentage() { return nullPercentage; }
        public void setNullPercentage(double nullPercentage) { this.nullPercentage = nullPercentage; }
        
        public int getDistinctValues() { return distinctValues; }
        public void setDistinctValues(int distinctValues) { this.distinctValues = distinctValues; }
        
        public String getSampleValue() { return sampleValue; }
        public void setSampleValue(String sampleValue) { this.sampleValue = sampleValue; }
        
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
    }
    
    // Database info inner class
    public static class DatabaseInfo {
        private String name;
        private String environment;
        private long collectionSize;
        private Date scanDate;
        
        public DatabaseInfo() {
            this.scanDate = new Date();
        }
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getEnvironment() { return environment; }
        public void setEnvironment(String environment) { this.environment = environment; }
        
        public long getCollectionSize() { return collectionSize; }
        public void setCollectionSize(long collectionSize) { this.collectionSize = collectionSize; }
        
        public Date getScanDate() { return scanDate; }
        public void setScanDate(Date scanDate) { this.scanDate = scanDate; }
    }
    
    // Main metadata getters and setters
    public String getExporterClass() { return exporterClass; }
    public void setExporterClass(String exporterClass) { this.exporterClass = exporterClass; }
    
    public Date getExportDate() { return exportDate; }
    public void setExportDate(Date exportDate) { this.exportDate = exportDate; }
    
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    
    public double getProcessingTimeSeconds() { return processingTimeSeconds; }
    public void setProcessingTimeSeconds(double processingTimeSeconds) { this.processingTimeSeconds = processingTimeSeconds; }
    
    public double getRowsPerSecond() { return rowsPerSecond; }
    public void setRowsPerSecond(double rowsPerSecond) { this.rowsPerSecond = rowsPerSecond; }
    
    public List<String> getHeaders() { return headers; }
    public void setHeaders(List<String> headers) { this.headers = headers; }
    
    public Map<Integer, ColumnMetadata> getColumns() { return columns; }
    public void setColumns(Map<Integer, ColumnMetadata> columns) { this.columns = columns; }
    
    public Map<String, Object> getExportOptions() { return exportOptions; }
    public void setExportOptions(Map<String, Object> exportOptions) { this.exportOptions = exportOptions; }
    
    public DatabaseInfo getDatabaseInfo() { return databaseInfo; }
    public void setDatabaseInfo(DatabaseInfo databaseInfo) { this.databaseInfo = databaseInfo; }
    
    public int getEmptyColumnCount() { return emptyColumnCount; }
    public void setEmptyColumnCount(int emptyColumnCount) { this.emptyColumnCount = emptyColumnCount; }
    
    public int getSingleValueColumnCount() { return singleValueColumnCount; }
    public void setSingleValueColumnCount(int singleValueColumnCount) { this.singleValueColumnCount = singleValueColumnCount; }
    
    public int getSparseColumnCount() { return sparseColumnCount; }
    public void setSparseColumnCount(int sparseColumnCount) { this.sparseColumnCount = sparseColumnCount; }
    
    public int getMeaningfulColumnCount() { return meaningfulColumnCount; }
    public void setMeaningfulColumnCount(int meaningfulColumnCount) { this.meaningfulColumnCount = meaningfulColumnCount; }
}