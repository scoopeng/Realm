package com.example.mongoexport;

/**
 * Export options for controlling export behavior
 */
public class ExportOptions {
    private boolean includeEmptyColumns = true;
    private boolean includeSingleValueColumns = true;
    private boolean includeSparseColumns = true;
    private double sparseThreshold = 95.0; // Percentage threshold for sparse columns
    private boolean enableRelationExpansion = true;  // Changed default to true
    private boolean enableFieldStatistics = false;
    private int expansionDepth = 3;  // Changed default to 3
    
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
    public boolean isEnableRelationExpansion() { return enableRelationExpansion; }
    public boolean isEnableFieldStatistics() { return enableFieldStatistics; }
    public int getExpansionDepth() { return expansionDepth; }
    public boolean isUseSavedSummary() { return false; } // Always false - no saved summaries in clean implementation
    
    // Setters - removed all unused setters
    // The class uses builder pattern, setters are not needed
}