package com.example.mongoexport.discovery;

import com.example.mongoexport.config.FieldConfiguration;
import com.example.mongoexport.FieldNameMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates statistics field configurations for arrays that reference transaction-like collections.
 * Analyzes target collections to identify numeric/date fields suitable for aggregation.
 */
public class StatisticsFieldGenerator {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsFieldGenerator.class);
    
    private final MongoDatabase database;
    private static final int SAMPLE_SIZE = 100;
    private static final int MIN_COLLECTION_SIZE = 100;
    private static final double MIN_NON_NULL_RATIO = 0.1; // At least 10% non-null
    private static final int MIN_DISTINCT_VALUES = 10;
    
    // Keywords indicating transaction-like collections
    private static final Set<String> TRANSACTION_KEYWORDS = Set.of(
        "transaction", "order", "sale", "purchase", "payment", "invoice", "deal", "contract"
    );
    
    // Keywords indicating numeric fields suitable for statistics
    private static final Set<String> NUMERIC_KEYWORDS = Set.of(
        "price", "amount", "cost", "value", "total", "sum", "fee", "rate", "commission",
        "volume", "quantity", "count", "revenue", "profit", "margin", "discount"
    );
    
    // Keywords indicating date fields
    private static final Set<String> DATE_KEYWORDS = Set.of(
        "date", "time", "timestamp", "created", "updated", "closed", "completed", 
        "modified", "processed", "started", "ended", "due"
    );
    
    // Keywords indicating duration fields
    private static final Set<String> DURATION_KEYWORDS = Set.of(
        "days", "hours", "minutes", "duration", "period", "time_on", "elapsed"
    );
    
    public StatisticsFieldGenerator(MongoDatabase database) {
        this.database = database;
    }
    
    /**
     * Analyzes an array field to determine if statistics fields should be generated.
     * Returns a list of statistics field configurations if applicable.
     */
    public List<FieldConfiguration> generateStatisticsFields(
            String arrayFieldPath,
            String sourceCollection,
            String targetCollection,
            String referenceField) {
        
        List<FieldConfiguration> statisticsFields = new ArrayList<>();
        
        // Check if target collection exists and is suitable for statistics
        if (!isCollectionSuitableForStatistics(targetCollection)) {
            return statisticsFields;
        }
        
        // Analyze the target collection structure
        CollectionAnalysis analysis = analyzeCollection(targetCollection);
        if (analysis == null || !analysis.isTransactionLike()) {
            logger.debug("Collection {} is not transaction-like, skipping statistics generation", targetCollection);
            return statisticsFields;
        }
        
        logger.info("Generating statistics fields for {} -> {}", arrayFieldPath, targetCollection);
        
        // Generate count field (always)
        statisticsFields.add(createCountField(arrayFieldPath, sourceCollection, targetCollection, referenceField));
        
        // Generate sum/avg fields for numeric fields
        for (NumericFieldInfo numericField : analysis.numericFields) {
            if (numericField.isSuitableForSum()) {
                statisticsFields.add(createSumField(
                    arrayFieldPath, sourceCollection, targetCollection, referenceField, numericField
                ));
            }
            
            if (numericField.isSuitableForAverage()) {
                statisticsFields.add(createAvgField(
                    arrayFieldPath, sourceCollection, targetCollection, referenceField, numericField
                ));
            }
            
            // Add min/max for meaningful ranges
            if (numericField.hasSignificantRange()) {
                statisticsFields.add(createMinField(
                    arrayFieldPath, sourceCollection, targetCollection, referenceField, numericField
                ));
                statisticsFields.add(createMaxField(
                    arrayFieldPath, sourceCollection, targetCollection, referenceField, numericField
                ));
            }
        }
        
        // Generate date-based statistics
        for (DateFieldInfo dateField : analysis.dateFields) {
            if (dateField.isMainDateField()) {
                // Most recent transaction
                statisticsFields.add(createMostRecentField(
                    arrayFieldPath, sourceCollection, targetCollection, referenceField, dateField
                ));
                
                // Oldest transaction
                statisticsFields.add(createOldestField(
                    arrayFieldPath, sourceCollection, targetCollection, referenceField, dateField
                ));
            }
        }
        
        // Limit to reasonable number of statistics fields
        if (statisticsFields.size() > 10) {
            logger.info("Generated {} statistics fields, limiting to top 10", statisticsFields.size());
            return statisticsFields.stream()
                .limit(10)
                .collect(Collectors.toList());
        }
        
        return statisticsFields;
    }
    
    /**
     * Checks if a collection is suitable for statistics generation
     */
    private boolean isCollectionSuitableForStatistics(String collectionName) {
        try {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            long count = collection.countDocuments();
            
            if (count < MIN_COLLECTION_SIZE) {
                logger.debug("Collection {} has only {} documents, too small for statistics", 
                    collectionName, count);
                return false;
            }
            
            // Check if collection name suggests it's transaction-like
            String lowerName = collectionName.toLowerCase();
            boolean nameIndicatesTransactions = TRANSACTION_KEYWORDS.stream()
                .anyMatch(lowerName::contains);
            
            if (nameIndicatesTransactions) {
                logger.debug("Collection {} appears transaction-like based on name", collectionName);
                return true;
            }
            
            // For other collections, need deeper analysis
            return true; // Will analyze structure next
            
        } catch (Exception e) {
            logger.error("Error checking collection {}: {}", collectionName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Analyzes a collection's structure to identify fields suitable for statistics
     */
    private CollectionAnalysis analyzeCollection(String collectionName) {
        try {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            CollectionAnalysis analysis = new CollectionAnalysis(collectionName);
            
            // Sample documents to understand structure
            List<Document> samples = new ArrayList<>();
            try (MongoCursor<Document> cursor = collection.find()
                    .limit(SAMPLE_SIZE)
                    .iterator()) {
                while (cursor.hasNext() && samples.size() < SAMPLE_SIZE) {
                    samples.add(cursor.next());
                }
            }
            
            if (samples.isEmpty()) {
                return null;
            }
            
            // Analyze field types and patterns
            Map<String, FieldTypeInfo> fieldTypes = new HashMap<>();
            
            for (Document doc : samples) {
                analyzeDocument(doc, "", fieldTypes);
            }
            
            // Process field analysis results
            for (Map.Entry<String, FieldTypeInfo> entry : fieldTypes.entrySet()) {
                String fieldPath = entry.getKey();
                FieldTypeInfo typeInfo = entry.getValue();
                
                if (typeInfo.isNumericField() && typeInfo.getNonNullRatio() >= MIN_NON_NULL_RATIO) {
                    NumericFieldInfo numInfo = new NumericFieldInfo(fieldPath, typeInfo);
                    if (numInfo.distinctValues >= MIN_DISTINCT_VALUES) {
                        analysis.numericFields.add(numInfo);
                    }
                }
                
                if (typeInfo.isDateField() && typeInfo.getNonNullRatio() >= MIN_NON_NULL_RATIO) {
                    analysis.dateFields.add(new DateFieldInfo(fieldPath, typeInfo));
                }
            }
            
            logger.debug("Collection {} analysis: {} numeric fields, {} date fields",
                collectionName, analysis.numericFields.size(), analysis.dateFields.size());
            
            return analysis;
            
        } catch (Exception e) {
            logger.error("Error analyzing collection {}: {}", collectionName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Recursively analyzes document structure
     */
    private void analyzeDocument(Document doc, String prefix, Map<String, FieldTypeInfo> fieldTypes) {
        for (String key : doc.keySet()) {
            if (key.startsWith("_")) continue; // Skip internal fields
            
            String fieldPath = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = doc.get(key);
            
            if (value == null) {
                fieldTypes.computeIfAbsent(fieldPath, k -> new FieldTypeInfo(k))
                    .addNullValue();
            } else if (value instanceof Number || value instanceof Decimal128) {
                fieldTypes.computeIfAbsent(fieldPath, k -> new FieldTypeInfo(k))
                    .addNumericValue(value);
            } else if (value instanceof Date) {
                fieldTypes.computeIfAbsent(fieldPath, k -> new FieldTypeInfo(k))
                    .addDateValue((Date) value);
            } else if (value instanceof Document) {
                // Recurse into subdocuments (but limit depth)
                if (prefix.split("\\.").length < 3) {
                    analyzeDocument((Document) value, fieldPath, fieldTypes);
                }
            }
            // Skip arrays and other types for statistics
        }
    }
    
    // Field creation methods
    
    private FieldConfiguration createCountField(String arrayPath, String sourceCollection, 
                                               String targetCollection, String referenceField) {
        String fieldPath = arrayPath + "[stats].count";
        FieldConfiguration field = new FieldConfiguration(fieldPath);
        field.setSourceCollection(sourceCollection);
        field.setDataType("integer");
        field.setBusinessName(FieldNameMapper.getBusinessName(arrayPath) + " Count");
        field.setInclude(false); // Let user decide
        field.setExtractionMode("statistics");
        
        // Set statistics configuration
        FieldConfiguration.StatisticsConfiguration statsConfig = new FieldConfiguration.StatisticsConfiguration();
        statsConfig.setSourceField(arrayPath);
        statsConfig.setTargetCollection(targetCollection);
        statsConfig.setAggregation("count");
        statsConfig.setMatchField(referenceField);
        field.setStatisticsConfig(statsConfig);
        
        return field;
    }
    
    private FieldConfiguration createSumField(String arrayPath, String sourceCollection,
                                             String targetCollection, String referenceField,
                                             NumericFieldInfo numericField) {
        String fieldPath = arrayPath + "[stats].sum_" + sanitizeFieldName(numericField.fieldPath);
        FieldConfiguration field = new FieldConfiguration(fieldPath);
        field.setSourceCollection(sourceCollection);
        field.setDataType(numericField.isDecimal ? "decimal" : "long");
        field.setBusinessName("Total " + humanizeFieldName(numericField.fieldPath));
        field.setInclude(false);
        field.setExtractionMode("statistics");
        
        FieldConfiguration.StatisticsConfiguration statsConfig = new FieldConfiguration.StatisticsConfiguration();
        statsConfig.setSourceField(arrayPath);
        statsConfig.setTargetCollection(targetCollection);
        statsConfig.setAggregation("sum");
        statsConfig.setTargetField(numericField.fieldPath);
        statsConfig.setMatchField(referenceField);
        field.setStatisticsConfig(statsConfig);
        
        return field;
    }
    
    private FieldConfiguration createAvgField(String arrayPath, String sourceCollection,
                                             String targetCollection, String referenceField,
                                             NumericFieldInfo numericField) {
        String fieldPath = arrayPath + "[stats].avg_" + sanitizeFieldName(numericField.fieldPath);
        FieldConfiguration field = new FieldConfiguration(fieldPath);
        field.setSourceCollection(sourceCollection);
        field.setDataType("double");
        field.setBusinessName("Average " + humanizeFieldName(numericField.fieldPath));
        field.setInclude(false);
        field.setExtractionMode("statistics");
        
        FieldConfiguration.StatisticsConfiguration statsConfig = new FieldConfiguration.StatisticsConfiguration();
        statsConfig.setSourceField(arrayPath);
        statsConfig.setTargetCollection(targetCollection);
        statsConfig.setAggregation("avg");
        statsConfig.setTargetField(numericField.fieldPath);
        statsConfig.setMatchField(referenceField);
        field.setStatisticsConfig(statsConfig);
        
        return field;
    }
    
    private FieldConfiguration createMinField(String arrayPath, String sourceCollection,
                                             String targetCollection, String referenceField,
                                             NumericFieldInfo numericField) {
        String fieldPath = arrayPath + "[stats].min_" + sanitizeFieldName(numericField.fieldPath);
        FieldConfiguration field = new FieldConfiguration(fieldPath);
        field.setSourceCollection(sourceCollection);
        field.setDataType(numericField.isDecimal ? "decimal" : "long");
        field.setBusinessName("Minimum " + humanizeFieldName(numericField.fieldPath));
        field.setInclude(false);
        field.setExtractionMode("statistics");
        
        FieldConfiguration.StatisticsConfiguration statsConfig = new FieldConfiguration.StatisticsConfiguration();
        statsConfig.setSourceField(arrayPath);
        statsConfig.setTargetCollection(targetCollection);
        statsConfig.setAggregation("min");
        statsConfig.setTargetField(numericField.fieldPath);
        statsConfig.setMatchField(referenceField);
        field.setStatisticsConfig(statsConfig);
        
        return field;
    }
    
    private FieldConfiguration createMaxField(String arrayPath, String sourceCollection,
                                             String targetCollection, String referenceField,
                                             NumericFieldInfo numericField) {
        String fieldPath = arrayPath + "[stats].max_" + sanitizeFieldName(numericField.fieldPath);
        FieldConfiguration field = new FieldConfiguration(fieldPath);
        field.setSourceCollection(sourceCollection);
        field.setDataType(numericField.isDecimal ? "decimal" : "long");
        field.setBusinessName("Maximum " + humanizeFieldName(numericField.fieldPath));
        field.setInclude(false);
        field.setExtractionMode("statistics");
        
        FieldConfiguration.StatisticsConfiguration statsConfig = new FieldConfiguration.StatisticsConfiguration();
        statsConfig.setSourceField(arrayPath);
        statsConfig.setTargetCollection(targetCollection);
        statsConfig.setAggregation("max");
        statsConfig.setTargetField(numericField.fieldPath);
        statsConfig.setMatchField(referenceField);
        field.setStatisticsConfig(statsConfig);
        
        return field;
    }
    
    private FieldConfiguration createMostRecentField(String arrayPath, String sourceCollection,
                                                    String targetCollection, String referenceField,
                                                    DateFieldInfo dateField) {
        String fieldPath = arrayPath + "[stats].most_recent_" + sanitizeFieldName(dateField.fieldPath);
        FieldConfiguration field = new FieldConfiguration(fieldPath);
        field.setSourceCollection(sourceCollection);
        field.setDataType("date");
        field.setBusinessName("Most Recent " + humanizeFieldName(dateField.fieldPath));
        field.setInclude(false);
        field.setExtractionMode("statistics");
        
        FieldConfiguration.StatisticsConfiguration statsConfig = new FieldConfiguration.StatisticsConfiguration();
        statsConfig.setSourceField(arrayPath);
        statsConfig.setTargetCollection(targetCollection);
        statsConfig.setAggregation("max");
        statsConfig.setTargetField(dateField.fieldPath);
        statsConfig.setMatchField(referenceField);
        field.setStatisticsConfig(statsConfig);
        
        return field;
    }
    
    private FieldConfiguration createOldestField(String arrayPath, String sourceCollection,
                                                String targetCollection, String referenceField,
                                                DateFieldInfo dateField) {
        String fieldPath = arrayPath + "[stats].oldest_" + sanitizeFieldName(dateField.fieldPath);
        FieldConfiguration field = new FieldConfiguration(fieldPath);
        field.setSourceCollection(sourceCollection);
        field.setDataType("date");
        field.setBusinessName("Oldest " + humanizeFieldName(dateField.fieldPath));
        field.setInclude(false);
        field.setExtractionMode("statistics");
        
        FieldConfiguration.StatisticsConfiguration statsConfig = new FieldConfiguration.StatisticsConfiguration();
        statsConfig.setSourceField(arrayPath);
        statsConfig.setTargetCollection(targetCollection);
        statsConfig.setAggregation("min");
        statsConfig.setTargetField(dateField.fieldPath);
        statsConfig.setMatchField(referenceField);
        field.setStatisticsConfig(statsConfig);
        
        return field;
    }
    
    // Helper methods
    
    private String sanitizeFieldName(String fieldPath) {
        return fieldPath.replace(".", "_").replace(" ", "_").toLowerCase();
    }
    
    private String humanizeFieldName(String fieldPath) {
        String[] parts = fieldPath.split("\\.");
        String lastPart = parts[parts.length - 1];
        
        // Convert snake_case to Title Case
        return Arrays.stream(lastPart.split("_"))
            .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }
    
    // Inner classes for analysis results
    
    private static class CollectionAnalysis {
        final String collectionName;
        final List<NumericFieldInfo> numericFields = new ArrayList<>();
        final List<DateFieldInfo> dateFields = new ArrayList<>();
        
        CollectionAnalysis(String collectionName) {
            this.collectionName = collectionName;
        }
        
        boolean isTransactionLike() {
            // Must have at least one numeric field and one date field
            if (numericFields.isEmpty() || dateFields.isEmpty()) {
                return false;
            }
            
            // Check if any numeric field looks like money/quantity
            boolean hasMoneyField = numericFields.stream()
                .anyMatch(f -> f.looksLikeMoney() || f.looksLikeQuantity());
            
            // Check collection name
            boolean nameIndicatesTransactions = TRANSACTION_KEYWORDS.stream()
                .anyMatch(keyword -> collectionName.toLowerCase().contains(keyword));
            
            return hasMoneyField || nameIndicatesTransactions;
        }
    }
    
    private static class NumericFieldInfo {
        final String fieldPath;
        final int distinctValues;
        final double minValue;
        final double maxValue;
        final boolean isDecimal;
        final int nonNullCount;
        
        NumericFieldInfo(String fieldPath, FieldTypeInfo typeInfo) {
            this.fieldPath = fieldPath;
            this.distinctValues = typeInfo.distinctValues.size();
            this.minValue = typeInfo.minValue;
            this.maxValue = typeInfo.maxValue;
            this.isDecimal = typeInfo.hasDecimals;
            this.nonNullCount = typeInfo.nonNullCount;
        }
        
        boolean looksLikeMoney() {
            String lower = fieldPath.toLowerCase();
            return NUMERIC_KEYWORDS.stream().anyMatch(lower::contains) ||
                   lower.contains("price") || lower.contains("amount") || 
                   lower.contains("cost") || lower.contains("value");
        }
        
        boolean looksLikeQuantity() {
            String lower = fieldPath.toLowerCase();
            return lower.contains("quantity") || lower.contains("count") || 
                   lower.contains("number") || lower.contains("qty");
        }
        
        boolean looksLikeDuration() {
            String lower = fieldPath.toLowerCase();
            return DURATION_KEYWORDS.stream().anyMatch(lower::contains);
        }
        
        boolean isSuitableForSum() {
            return looksLikeMoney() || looksLikeQuantity();
        }
        
        boolean isSuitableForAverage() {
            return looksLikeMoney() || looksLikeQuantity() || looksLikeDuration();
        }
        
        boolean hasSignificantRange() {
            return (maxValue - minValue) > 0 && distinctValues > 10;
        }
    }
    
    private static class DateFieldInfo {
        final String fieldPath;
        final Date minDate;
        final Date maxDate;
        final int nonNullCount;
        
        DateFieldInfo(String fieldPath, FieldTypeInfo typeInfo) {
            this.fieldPath = fieldPath;
            this.minDate = typeInfo.minDate;
            this.maxDate = typeInfo.maxDate;
            this.nonNullCount = typeInfo.nonNullCount;
        }
        
        boolean isMainDateField() {
            String lower = fieldPath.toLowerCase();
            return lower.contains("closing") || lower.contains("completed") ||
                   lower.contains("transaction") || lower.contains("created");
        }
    }
    
    private static class FieldTypeInfo {
        final String fieldPath;
        int nullCount = 0;
        int nonNullCount = 0;
        Set<Object> distinctValues = new HashSet<>();
        
        // For numeric fields
        double minValue = Double.MAX_VALUE;
        double maxValue = Double.MIN_VALUE;
        boolean hasDecimals = false;
        
        // For date fields
        Date minDate = null;
        Date maxDate = null;
        
        // Type flags
        boolean isNumeric = false;
        boolean isDate = false;
        
        FieldTypeInfo(String fieldPath) {
            this.fieldPath = fieldPath;
        }
        
        void addNullValue() {
            nullCount++;
        }
        
        void addNumericValue(Object value) {
            nonNullCount++;
            isNumeric = true;
            distinctValues.add(value);
            
            double doubleValue = 0;
            if (value instanceof Decimal128) {
                doubleValue = ((Decimal128) value).doubleValue();
                hasDecimals = true;
            } else if (value instanceof Double || value instanceof Float) {
                doubleValue = ((Number) value).doubleValue();
                hasDecimals = true;
            } else if (value instanceof Number) {
                doubleValue = ((Number) value).doubleValue();
            }
            
            minValue = Math.min(minValue, doubleValue);
            maxValue = Math.max(maxValue, doubleValue);
        }
        
        void addDateValue(Date value) {
            nonNullCount++;
            isDate = true;
            distinctValues.add(value);
            
            if (minDate == null || value.before(minDate)) {
                minDate = value;
            }
            if (maxDate == null || value.after(maxDate)) {
                maxDate = value;
            }
        }
        
        double getNonNullRatio() {
            int total = nullCount + nonNullCount;
            return total > 0 ? (double) nonNullCount / total : 0;
        }
        
        boolean isNumericField() {
            return isNumeric;
        }
        
        boolean isDateField() {
            return isDate;
        }
    }
}