package com.example.mongoexport;

/**
 * Export options for controlling export behavior
 */
public class ExportOptions {
    private boolean includeEmptyColumns = true;
    private boolean includeSingleValueColumns = true;
    private boolean includeSparseColumns = true;
    private double sparseThreshold = 95.0; // Percentage threshold for sparse columns
    private boolean scanFieldsFirst = false;
    private int scanSampleSize = 10000;
    private boolean generateMetadata = true;
    private boolean generateStatistics = true;
    private boolean enableRelationExpansion = false;
    private boolean enableFieldStatistics = false;
    private int expansionDepth = 2;
    private boolean useSavedSummary = false;
    
    // Builder pattern for easy configuration
    public static class Builder {
        private final ExportOptions options = new ExportOptions();
        
        public Builder excludeEmptyColumns() {
            options.includeEmptyColumns = false;
            return this;
        }
        
        public Builder excludeSingleValueColumns() {
            options.includeSingleValueColumns = false;
            return this;
        }
        
        public Builder excludeSparseColumns() {
            options.includeSparseColumns = false;
            return this;
        }
        
        public Builder sparseThreshold(double threshold) {
            options.sparseThreshold = threshold;
            return this;
        }
        
        public Builder scanFieldsFirst(boolean scan) {
            options.scanFieldsFirst = scan;
            return this;
        }
        
        public Builder scanSampleSize(int size) {
            options.scanSampleSize = size;
            return this;
        }
        
        public Builder generateMetadata(boolean generate) {
            options.generateMetadata = generate;
            return this;
        }
        
        public Builder generateStatistics(boolean generate) {
            options.generateStatistics = generate;
            return this;
        }
        
        public Builder enableRelationExpansion(boolean enable) {
            options.enableRelationExpansion = enable;
            return this;
        }
        
        public Builder enableFieldStatistics(boolean enable) {
            options.enableFieldStatistics = enable;
            return this;
        }
        
        public Builder expansionDepth(int depth) {
            options.expansionDepth = depth;
            return this;
        }
        
        public Builder useSavedSummary(boolean use) {
            options.useSavedSummary = use;
            return this;
        }
        
        public ExportOptions build() {
            return options;
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public boolean isIncludeEmptyColumns() { return includeEmptyColumns; }
    public boolean isIncludeSingleValueColumns() { return includeSingleValueColumns; }
    public boolean isIncludeSparseColumns() { return includeSparseColumns; }
    public double getSparseThreshold() { return sparseThreshold; }
    public boolean isScanFieldsFirst() { return scanFieldsFirst; }
    public int getScanSampleSize() { return scanSampleSize; }
    public boolean isGenerateMetadata() { return generateMetadata; }
    public boolean isGenerateStatistics() { return generateStatistics; }
    public boolean isEnableRelationExpansion() { return enableRelationExpansion; }
    public boolean isEnableFieldStatistics() { return enableFieldStatistics; }
    public int getExpansionDepth() { return expansionDepth; }
    public boolean isUseSavedSummary() { return useSavedSummary; }
    
    // Setters
    public void setIncludeEmptyColumns(boolean includeEmptyColumns) {
        this.includeEmptyColumns = includeEmptyColumns;
    }
    
    public void setIncludeSingleValueColumns(boolean includeSingleValueColumns) {
        this.includeSingleValueColumns = includeSingleValueColumns;
    }
    
    public void setIncludeSparseColumns(boolean includeSparseColumns) {
        this.includeSparseColumns = includeSparseColumns;
    }
    
    public void setSparseThreshold(double sparseThreshold) {
        this.sparseThreshold = sparseThreshold;
    }
    
    public void setScanFieldsFirst(boolean scanFieldsFirst) {
        this.scanFieldsFirst = scanFieldsFirst;
    }
    
    public void setScanSampleSize(int scanSampleSize) {
        this.scanSampleSize = scanSampleSize;
    }
    
    public void setGenerateMetadata(boolean generateMetadata) {
        this.generateMetadata = generateMetadata;
    }
    
    public void setGenerateStatistics(boolean generateStatistics) {
        this.generateStatistics = generateStatistics;
    }
    
    public void setEnableRelationExpansion(boolean enableRelationExpansion) {
        this.enableRelationExpansion = enableRelationExpansion;
    }
    
    public void setEnableFieldStatistics(boolean enableFieldStatistics) {
        this.enableFieldStatistics = enableFieldStatistics;
    }
    
    public void setExpansionDepth(int expansionDepth) {
        this.expansionDepth = expansionDepth;
    }
    
    public void setUseSavedSummary(boolean useSavedSummary) {
        this.useSavedSummary = useSavedSummary;
    }
}