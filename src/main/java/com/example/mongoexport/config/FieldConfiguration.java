package com.example.mongoexport.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a single field discovered during the discovery phase.
 * This represents all metadata about a field that will be used during export.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldConfiguration {
    
    @JsonProperty("fieldPath")
    private String fieldPath;
    
    @JsonProperty("businessName")
    private String businessName;
    
    @JsonProperty("sourceCollection")
    private String sourceCollection;
    
    @JsonProperty("dataType")
    private String dataType; // string, number, boolean, date, objectId, object, array
    
    @JsonProperty("include")
    private boolean include = true;
    
    @JsonProperty("statistics")
    private FieldStatistics statistics;
    
    @JsonProperty("arrayConfig")
    private ArrayConfiguration arrayConfig;
    
    @JsonProperty("objectFields")
    private List<FieldConfiguration> objectFields; // For nested objects or array of objects
    
    @JsonProperty("relationshipTarget")
    private String relationshipTarget; // Target collection for ObjectId references
    
    // Constructors
    public FieldConfiguration() {}
    
    public FieldConfiguration(String fieldPath) {
        this.fieldPath = fieldPath;
    }
    
    // Statistics inner class
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldStatistics {
        @JsonProperty("distinctNonNullValues")
        private Integer distinctNonNullValues;
        
        @JsonProperty("nullCount")
        private Integer nullCount;
        
        @JsonProperty("totalOccurrences")
        private Integer totalOccurrences;
        
        @JsonProperty("sampleValues")
        private List<String> sampleValues;
        
        @JsonProperty("avgArrayLength")
        private Double avgArrayLength;
        
        @JsonProperty("maxArrayLength")
        private Integer maxArrayLength;
        
        // Getters and setters
        public Integer getDistinctNonNullValues() { return distinctNonNullValues; }
        public void setDistinctNonNullValues(Integer value) { this.distinctNonNullValues = value; }
        
        public Integer getNullCount() { return nullCount; }
        public void setNullCount(Integer value) { this.nullCount = value; }
        
        public Integer getTotalOccurrences() { return totalOccurrences; }
        public void setTotalOccurrences(Integer value) { this.totalOccurrences = value; }
        
        public List<String> getSampleValues() { return sampleValues; }
        public void setSampleValues(List<String> values) { this.sampleValues = values; }
        
        public Double getAvgArrayLength() { return avgArrayLength; }
        public void setAvgArrayLength(Double value) { this.avgArrayLength = value; }
        
        public Integer getMaxArrayLength() { return maxArrayLength; }
        public void setMaxArrayLength(Integer value) { this.maxArrayLength = value; }
    }
    
    // Array configuration inner class
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ArrayConfiguration {
        @JsonProperty("extractField")
        private String extractField; // Which field to extract from array objects or referenced documents
        
        @JsonProperty("displayMode")
        private String displayMode = "comma_separated"; // "first" or "comma_separated"
        
        @JsonProperty("sortOrder")
        private String sortOrder = "alphanumeric"; // "alphanumeric", "none", "numeric"
        
        @JsonProperty("delimiter")
        private String delimiter = ", ";
        
        @JsonProperty("objectType")
        private String objectType; // Type of objects in array: "string", "object", "objectId"
        
        @JsonProperty("referenceField")
        private String referenceField; // For arrays of objects, which field contains the ObjectId reference
        
        @JsonProperty("referenceCollection")
        private String referenceCollection; // Collection that ObjectIds reference
        
        @JsonProperty("availableFields")
        private List<String> availableFields; // Available fields from the referenced collection that can be used as extractField
        
        // Getters and setters
        public String getExtractField() { return extractField; }
        public void setExtractField(String field) { this.extractField = field; }
        
        public String getDisplayMode() { return displayMode; }
        public void setDisplayMode(String mode) { this.displayMode = mode; }
        
        public String getSortOrder() { return sortOrder; }
        public void setSortOrder(String order) { this.sortOrder = order; }
        
        public String getDelimiter() { return delimiter; }
        public void setDelimiter(String delimiter) { this.delimiter = delimiter; }
        
        public String getObjectType() { return objectType; }
        public void setObjectType(String type) { this.objectType = type; }
        
        public String getReferenceField() { return referenceField; }
        public void setReferenceField(String field) { this.referenceField = field; }
        
        public String getReferenceCollection() { return referenceCollection; }
        public void setReferenceCollection(String collection) { this.referenceCollection = collection; }
        
        public List<String> getAvailableFields() { return availableFields; }
        public void setAvailableFields(List<String> fields) { this.availableFields = fields; }
    }
    
    // Main class getters and setters
    public String getFieldPath() { return fieldPath; }
    public void setFieldPath(String path) { this.fieldPath = path; }
    
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String name) { this.businessName = name; }
    
    public String getSourceCollection() { return sourceCollection; }
    public void setSourceCollection(String collection) { this.sourceCollection = collection; }
    
    public String getDataType() { return dataType; }
    public void setDataType(String type) { this.dataType = type; }
    
    public boolean isInclude() { return include; }
    public void setInclude(boolean include) { this.include = include; }
    
    public FieldStatistics getStatistics() { return statistics; }
    public void setStatistics(FieldStatistics stats) { this.statistics = stats; }
    
    public ArrayConfiguration getArrayConfig() { return arrayConfig; }
    public void setArrayConfig(ArrayConfiguration config) { this.arrayConfig = config; }
    
    public List<FieldConfiguration> getObjectFields() { return objectFields; }
    public void setObjectFields(List<FieldConfiguration> fields) { this.objectFields = fields; }
    
    public String getRelationshipTarget() { return relationshipTarget; }
    public void setRelationshipTarget(String target) { this.relationshipTarget = target; }
}