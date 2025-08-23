package com.example.mongoexport.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Configuration for a single field discovered during the discovery phase.
 * This represents all metadata about a field that will be used during export.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldConfiguration
{

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
    
    // New fields for primary mode extraction
    @JsonProperty("extractionMode")
    private String extractionMode; // "primary", "count", "statistics", or null for default
    
    @JsonProperty("sourceField")
    private String sourceField; // The array field this is extracted from
    
    @JsonProperty("extractionIndex")
    private Integer extractionIndex; // Which element to extract (0 for first)
    
    @JsonProperty("statisticsConfig")
    private StatisticsConfiguration statisticsConfig; // Configuration for statistics mode

    // Constructors
    public FieldConfiguration()
    {
    }

    public FieldConfiguration(String fieldPath)
    {
        this.fieldPath = fieldPath;
    }

    // Statistics inner class
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldStatistics
    {
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
        public Integer getDistinctNonNullValues()
        {
            return distinctNonNullValues;
        }

        public void setDistinctNonNullValues(Integer value)
        {
            this.distinctNonNullValues = value;
        }

        public Integer getNullCount()
        {
            return nullCount;
        }

        public void setNullCount(Integer value)
        {
            this.nullCount = value;
        }

        public Integer getTotalOccurrences()
        {
            return totalOccurrences;
        }

        public void setTotalOccurrences(Integer value)
        {
            this.totalOccurrences = value;
        }

        public List<String> getSampleValues()
        {
            return sampleValues;
        }

        public void setSampleValues(List<String> values)
        {
            this.sampleValues = values;
        }

        public Double getAvgArrayLength()
        {
            return avgArrayLength;
        }

        public void setAvgArrayLength(Double value)
        {
            this.avgArrayLength = value;
        }

        public Integer getMaxArrayLength()
        {
            return maxArrayLength;
        }

        public void setMaxArrayLength(Integer value)
        {
            this.maxArrayLength = value;
        }
    }

    // Array configuration inner class
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ArrayConfiguration
    {
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
        public String getExtractField()
        {
            return extractField;
        }

        public void setExtractField(String field)
        {
            this.extractField = field;
        }

        public String getDisplayMode()
        {
            return displayMode;
        }

        public void setDisplayMode(String mode)
        {
            this.displayMode = mode;
        }

        public String getSortOrder()
        {
            return sortOrder;
        }

        public void setSortOrder(String order)
        {
            this.sortOrder = order;
        }

        public String getDelimiter()
        {
            return delimiter;
        }

        public void setDelimiter(String delimiter)
        {
            this.delimiter = delimiter;
        }

        public String getObjectType()
        {
            return objectType;
        }

        public void setObjectType(String type)
        {
            this.objectType = type;
        }

        public String getReferenceField()
        {
            return referenceField;
        }

        public void setReferenceField(String field)
        {
            this.referenceField = field;
        }

        public String getReferenceCollection()
        {
            return referenceCollection;
        }

        public void setReferenceCollection(String collection)
        {
            this.referenceCollection = collection;
        }

        public List<String> getAvailableFields()
        {
            return availableFields;
        }

        public void setAvailableFields(List<String> fields)
        {
            this.availableFields = fields;
        }
    }

    // Main class getters and setters
    public String getFieldPath()
    {
        return fieldPath;
    }

    public void setFieldPath(String path)
    {
        this.fieldPath = path;
    }

    public String getBusinessName()
    {
        return businessName;
    }

    public void setBusinessName(String name)
    {
        this.businessName = name;
    }

    public String getSourceCollection()
    {
        return sourceCollection;
    }

    public void setSourceCollection(String collection)
    {
        this.sourceCollection = collection;
    }

    public String getDataType()
    {
        return dataType;
    }

    public void setDataType(String type)
    {
        this.dataType = type;
    }

    public boolean isInclude()
    {
        return include;
    }

    public void setInclude(boolean include)
    {
        this.include = include;
    }

    public FieldStatistics getStatistics()
    {
        return statistics;
    }

    public void setStatistics(FieldStatistics stats)
    {
        this.statistics = stats;
    }

    public ArrayConfiguration getArrayConfig()
    {
        return arrayConfig;
    }

    public void setArrayConfig(ArrayConfiguration config)
    {
        this.arrayConfig = config;
    }

    public List<FieldConfiguration> getObjectFields()
    {
        return objectFields;
    }

    public void setObjectFields(List<FieldConfiguration> fields)
    {
        this.objectFields = fields;
    }

    public String getRelationshipTarget()
    {
        return relationshipTarget;
    }

    public void setRelationshipTarget(String target)
    {
        this.relationshipTarget = target;
    }
    
    // Getters and setters for extraction mode fields
    public String getExtractionMode()
    {
        return extractionMode;
    }
    
    public void setExtractionMode(String mode)
    {
        this.extractionMode = mode;
    }
    
    public String getSourceField()
    {
        return sourceField;
    }
    
    public void setSourceField(String field)
    {
        this.sourceField = field;
    }
    
    public Integer getExtractionIndex()
    {
        return extractionIndex;
    }
    
    public void setExtractionIndex(Integer index)
    {
        this.extractionIndex = index;
    }
    
    public StatisticsConfiguration getStatisticsConfig()
    {
        return statisticsConfig;
    }
    
    public void setStatisticsConfig(StatisticsConfiguration config)
    {
        this.statisticsConfig = config;
    }
    
    /**
     * Configuration for statistics mode fields
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StatisticsConfiguration
    {
        @JsonProperty("sourceField")
        private String sourceField; // The array field to calculate statistics for
        
        @JsonProperty("targetCollection")
        private String targetCollection; // Collection to query for statistics
        
        @JsonProperty("aggregation")
        private String aggregation; // Type: count, sum, avg, min, max, median
        
        @JsonProperty("targetField")
        private String targetField; // Field in target collection to aggregate
        
        @JsonProperty("matchField")
        private String matchField; // Field in target collection to match against
        
        @JsonProperty("dateFilter")
        private DateFilterConfiguration dateFilter; // Optional date range filter
        
        @JsonProperty("groupBy")
        private String groupBy; // Optional grouping (month, year, etc.)
        
        // Getters and setters
        public String getSourceField()
        {
            return sourceField;
        }
        
        public void setSourceField(String field)
        {
            this.sourceField = field;
        }
        
        public String getTargetCollection()
        {
            return targetCollection;
        }
        
        public void setTargetCollection(String collection)
        {
            this.targetCollection = collection;
        }
        
        public String getAggregation()
        {
            return aggregation;
        }
        
        public void setAggregation(String agg)
        {
            this.aggregation = agg;
        }
        
        public String getTargetField()
        {
            return targetField;
        }
        
        public void setTargetField(String field)
        {
            this.targetField = field;
        }
        
        public String getMatchField()
        {
            return matchField;
        }
        
        public void setMatchField(String field)
        {
            this.matchField = field;
        }
        
        public DateFilterConfiguration getDateFilter()
        {
            return dateFilter;
        }
        
        public void setDateFilter(DateFilterConfiguration filter)
        {
            this.dateFilter = filter;
        }
        
        public String getGroupBy()
        {
            return groupBy;
        }
        
        public void setGroupBy(String group)
        {
            this.groupBy = group;
        }
    }
    
    /**
     * Date filter configuration for statistics
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DateFilterConfiguration
    {
        @JsonProperty("field")
        private String field; // Date field to filter on
        
        @JsonProperty("range")
        private String range; // Predefined range: last_year, last_month, last_30_days, etc.
        
        @JsonProperty("startDate")
        private String startDate; // Custom start date (ISO format)
        
        @JsonProperty("endDate")
        private String endDate; // Custom end date (ISO format)
        
        // Getters and setters
        public String getField()
        {
            return field;
        }
        
        public void setField(String field)
        {
            this.field = field;
        }
        
        public String getRange()
        {
            return range;
        }
        
        public void setRange(String range)
        {
            this.range = range;
        }
        
        public String getStartDate()
        {
            return startDate;
        }
        
        public void setStartDate(String date)
        {
            this.startDate = date;
        }
        
        public String getEndDate()
        {
            return endDate;
        }
        
        public void setEndDate(String date)
        {
            this.endDate = date;
        }
    }
}