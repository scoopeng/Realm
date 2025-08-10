package com.example.mongoexport;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility to scan MongoDB collections and analyze field usage
 */
public class FieldScanner {
    private static final Logger logger = LoggerFactory.getLogger(FieldScanner.class);
    private static final int SAMPLE_SIZE = 10000; // Default sample size
    
    private final MongoDatabase database;
    private final Map<String, FieldInfo> fieldStats = new ConcurrentHashMap<>();
    private int documentCount = 0;
    
    public FieldScanner(MongoDatabase database) {
        this.database = database;
    }
    
    /**
     * Scan a collection and analyze field usage
     */
    public FieldScanResult scanCollection(String collectionName) {
        return scanCollection(collectionName, SAMPLE_SIZE, true);
    }
    
    /**
     * Scan a collection with options
     */
    public FieldScanResult scanCollection(String collectionName, int sampleSize, boolean deepScan) {
        logger.info("Starting field scan of collection '{}' with sample size {}", collectionName, sampleSize);
        
        fieldStats.clear();
        documentCount = 0;
        
        MongoCollection<Document> collection = database.getCollection(collectionName);
        long totalDocuments = collection.countDocuments();
        
        try (MongoCursor<Document> cursor = collection.find().limit(sampleSize).iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                scanDocument(doc, "", deepScan);
                documentCount++;
                
                if (documentCount % 1000 == 0) {
                    logger.info("Scanned {} documents...", documentCount);
                }
            }
        }
        
        // Calculate statistics
        FieldScanResult result = new FieldScanResult();
        result.setCollectionName(collectionName);
        result.setTotalDocuments(totalDocuments);
        result.setScannedDocuments(documentCount);
        result.setScanDate(new Date());
        
        for (Map.Entry<String, FieldInfo> entry : fieldStats.entrySet()) {
            FieldInfo info = entry.getValue();
            info.calculateStatistics(documentCount);
            result.addField(info);
        }
        
        // Categorize fields
        result.categorizeFields();
        
        logger.info("Field scan completed. Scanned {} documents out of {} total", 
                   documentCount, totalDocuments);
        logger.info("Found {} unique fields", fieldStats.size());
        
        return result;
    }
    
    /**
     * Recursively scan a document
     */
    private void scanDocument(Document doc, String prefix, boolean deepScan) {
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            String fieldPath = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            
            FieldInfo fieldInfo = fieldStats.computeIfAbsent(fieldPath, k -> new FieldInfo(k));
            fieldInfo.incrementCount();
            
            if (value != null) {
                fieldInfo.incrementNonNullCount();
                fieldInfo.addSampleValue(value.toString());
                fieldInfo.detectType(value);
                
                // Deep scan for nested documents
                if (deepScan) {
                    if (value instanceof Document) {
                        scanDocument((Document) value, fieldPath, true);
                    } else if (value instanceof List) {
                        scanList((List<?>) value, fieldPath, fieldInfo);
                    }
                }
            }
        }
    }
    
    /**
     * Scan a list field
     */
    private void scanList(List<?> list, String fieldPath, FieldInfo fieldInfo) {
        fieldInfo.setIsArray(true);
        fieldInfo.recordArraySize(list.size());
        
        if (!list.isEmpty()) {
            Object firstItem = list.get(0);
            if (firstItem instanceof Document) {
                // Scan first document in array to get structure
                scanDocument((Document) firstItem, fieldPath + "[]", true);
            }
        }
    }
    
    /**
     * Field information class
     */
    public static class FieldInfo {
        private final String path;
        private int occurrenceCount = 0;
        private int nonNullCount = 0;
        private final Set<String> sampleValues = new HashSet<>();
        private final Set<String> dataTypes = new HashSet<>();
        private boolean isArray = false;
        private final List<Integer> arraySizes = new ArrayList<>();
        
        // Calculated statistics
        private double presencePercentage;
        private double nonNullPercentage;
        private int distinctValueCount;
        private String category;
        
        public FieldInfo(String path) {
            this.path = path;
        }
        
        public void incrementCount() {
            occurrenceCount++;
        }
        
        public void incrementNonNullCount() {
            nonNullCount++;
        }
        
        public void addSampleValue(String value) {
            if (sampleValues.size() < 100) { // Keep up to 100 sample values
                sampleValues.add(value);
            }
        }
        
        public void detectType(Object value) {
            dataTypes.add(value.getClass().getSimpleName());
        }
        
        public void setIsArray(boolean isArray) {
            this.isArray = isArray;
        }
        
        public void recordArraySize(int size) {
            arraySizes.add(size);
        }
        
        public void calculateStatistics(int totalDocuments) {
            presencePercentage = (occurrenceCount * 100.0) / totalDocuments;
            nonNullPercentage = occurrenceCount > 0 ? (nonNullCount * 100.0) / occurrenceCount : 0;
            distinctValueCount = sampleValues.size();
            
            // Categorize field
            if (nonNullCount == 0) {
                category = "always_null";
            } else if (distinctValueCount == 1 && nonNullCount == occurrenceCount) {
                category = "single_value";
            } else if (nonNullPercentage < 5) {
                category = "sparse";
            } else if (presencePercentage < 50) {
                category = "optional";
            } else {
                category = "common";
            }
        }
        
        // Getters
        public String getPath() { return path; }
        public int getOccurrenceCount() { return occurrenceCount; }
        public int getNonNullCount() { return nonNullCount; }
        public Set<String> getSampleValues() { return sampleValues; }
        public Set<String> getDataTypes() { return dataTypes; }
        public boolean isArray() { return isArray; }
        public List<Integer> getArraySizes() { return arraySizes; }
        public double getPresencePercentage() { return presencePercentage; }
        public double getNonNullPercentage() { return nonNullPercentage; }
        public int getDistinctValueCount() { return distinctValueCount; }
        public String getCategory() { return category; }
    }
    
    /**
     * Scan result class
     */
    public static class FieldScanResult {
        private String collectionName;
        private long totalDocuments;
        private int scannedDocuments;
        private Date scanDate;
        private final List<FieldInfo> fields = new ArrayList<>();
        
        // Field categories
        private final List<FieldInfo> alwaysNullFields = new ArrayList<>();
        private final List<FieldInfo> singleValueFields = new ArrayList<>();
        private final List<FieldInfo> sparseFields = new ArrayList<>();
        private final List<FieldInfo> optionalFields = new ArrayList<>();
        private final List<FieldInfo> commonFields = new ArrayList<>();
        
        public void addField(FieldInfo field) {
            fields.add(field);
        }
        
        public void categorizeFields() {
            for (FieldInfo field : fields) {
                switch (field.getCategory()) {
                    case "always_null":
                        alwaysNullFields.add(field);
                        break;
                    case "single_value":
                        singleValueFields.add(field);
                        break;
                    case "sparse":
                        sparseFields.add(field);
                        break;
                    case "optional":
                        optionalFields.add(field);
                        break;
                    case "common":
                        commonFields.add(field);
                        break;
                }
            }
            
            // Sort by path for consistency
            alwaysNullFields.sort(Comparator.comparing(FieldInfo::getPath));
            singleValueFields.sort(Comparator.comparing(FieldInfo::getPath));
            sparseFields.sort(Comparator.comparing(FieldInfo::getPath));
            optionalFields.sort(Comparator.comparing(FieldInfo::getPath));
            commonFields.sort(Comparator.comparing(FieldInfo::getPath));
        }
        
        public void printSummary() {
            logger.info("\n=== FIELD SCAN SUMMARY ===");
            logger.info("Collection: {}", collectionName);
            logger.info("Total documents: {}", totalDocuments);
            logger.info("Scanned documents: {}", scannedDocuments);
            logger.info("Total unique fields: {}", fields.size());
            logger.info("\nField Categories:");
            logger.info("  Always null: {} fields", alwaysNullFields.size());
            logger.info("  Single value: {} fields", singleValueFields.size());
            logger.info("  Sparse (<5% non-null): {} fields", sparseFields.size());
            logger.info("  Optional (<50% present): {} fields", optionalFields.size());
            logger.info("  Common (>=50% present): {} fields", commonFields.size());
            
            if (!alwaysNullFields.isEmpty()) {
                logger.info("\nAlways Null Fields (can be excluded):");
                alwaysNullFields.forEach(f -> logger.info("  - {}", f.getPath()));
            }
            
            if (!singleValueFields.isEmpty()) {
                logger.info("\nSingle Value Fields (can be excluded or made constant):");
                singleValueFields.forEach(f -> 
                    logger.info("  - {} = '{}'", f.getPath(), 
                               f.getSampleValues().iterator().next()));
            }
        }
        
        // Getters and setters
        public String getCollectionName() { return collectionName; }
        public void setCollectionName(String collectionName) { this.collectionName = collectionName; }
        
        public long getTotalDocuments() { return totalDocuments; }
        public void setTotalDocuments(long totalDocuments) { this.totalDocuments = totalDocuments; }
        
        public int getScannedDocuments() { return scannedDocuments; }
        public void setScannedDocuments(int scannedDocuments) { this.scannedDocuments = scannedDocuments; }
        
        public Date getScanDate() { return scanDate; }
        public void setScanDate(Date scanDate) { this.scanDate = scanDate; }
        
        public List<FieldInfo> getFields() { return fields; }
        public List<FieldInfo> getAlwaysNullFields() { return alwaysNullFields; }
        public List<FieldInfo> getSingleValueFields() { return singleValueFields; }
        public List<FieldInfo> getSparseFields() { return sparseFields; }
        public List<FieldInfo> getOptionalFields() { return optionalFields; }
        public List<FieldInfo> getCommonFields() { return commonFields; }
    }
}