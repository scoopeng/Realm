package com.example.mongoexport;

import com.mongodb.client.*;
import com.opencsv.CSVWriter;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class MongoToCSVExporter {
    private static final Logger logger = LoggerFactory.getLogger(MongoToCSVExporter.class);
    
    private final ExportConfig config;
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    
    public MongoToCSVExporter(ExportConfig config) {
        this.config = config;
        
        try {
            logger.info("Connecting to MongoDB...");
            this.mongoClient = MongoClients.create(config.getMongoUrl());
            this.database = mongoClient.getDatabase(config.getDatabaseName());
            logger.info("Successfully connected to MongoDB database: {}", config.getDatabaseName());
        } catch (Exception e) {
            logger.error("Failed to connect to MongoDB", e);
            throw new RuntimeException("Failed to connect to MongoDB", e);
        }
    }
    
    public void exportCollection(String collectionName, List<String> multiValueFieldNames, List<String> fieldsToExport) {
        logger.info("Starting export of collection: {}", collectionName);
        logger.info("Multi-value fields: {}", multiValueFieldNames);
        logger.info("Fields to export: {}", fieldsToExport);
        logger.info("Export strategy: {}", config.getExportStrategy());
        
        try {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            long documentCount = collection.countDocuments();
            logger.info("Total documents in collection: {}", documentCount);
            
            if (documentCount == 0) {
                logger.warn("Collection {} is empty. Skipping export.", collectionName);
                return;
            }
            
            String fileName = generateFileName(collectionName);
            Path filePath = Paths.get(config.getOutputDirectory(), fileName);
            
            // Use tab delimiter for MySQL bulk loading compatibility
            try (CSVWriter writer = new CSVWriter(new FileWriter(filePath.toString(), StandardCharsets.UTF_8), 
                    '\t', CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END)) {
                // Write header
                String[] header = fieldsToExport.toArray(new String[0]);
                writer.writeNext(header);
                
                // Export documents based on strategy
                if (config.getExportStrategy() == ExportStrategy.DENORMALIZED) {
                    exportDenormalized(collection, multiValueFieldNames, fieldsToExport, writer);
                } else {
                    exportDelimited(collection, multiValueFieldNames, fieldsToExport, writer);
                }
                
                logger.info("Export completed successfully. File written to: {}", filePath);
            }
            
        } catch (Exception e) {
            logger.error("Failed to export collection: {}", collectionName, e);
            throw new RuntimeException("Failed to export collection: " + collectionName, e);
        }
    }
    
    private void exportDenormalized(MongoCollection<Document> collection, 
                                   List<String> multiValueFieldNames, 
                                   List<String> fieldsToExport, 
                                   CSVWriter writer) {
        
        int documentsProcessed = 0;
        int rowsWritten = 0;
        
        try (MongoCursor<Document> cursor = collection.find().iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                documentsProcessed++;
                
                // Get all multi-value field combinations
                List<Map<String, String>> combinations = generateCombinations(doc, multiValueFieldNames);
                
                if (combinations.isEmpty()) {
                    // No multi-value fields or all are empty/null, write single row
                    String[] row = extractRow(doc, fieldsToExport, Collections.emptyMap());
                    writer.writeNext(row);
                    rowsWritten++;
                } else {
                    // Write one row for each combination
                    for (Map<String, String> combination : combinations) {
                        String[] row = extractRow(doc, fieldsToExport, combination);
                        writer.writeNext(row);
                        rowsWritten++;
                    }
                }
                
                if (documentsProcessed % 1000 == 0) {
                    logger.info("Processed {} documents, written {} rows", documentsProcessed, rowsWritten);
                }
            }
        }
        
        logger.info("Denormalized export complete. Processed {} documents, written {} rows", 
                   documentsProcessed, rowsWritten);
    }
    
    private void exportDelimited(MongoCollection<Document> collection, 
                               List<String> multiValueFieldNames, 
                               List<String> fieldsToExport, 
                               CSVWriter writer) {
        
        int documentsProcessed = 0;
        
        try (MongoCursor<Document> cursor = collection.find().iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                documentsProcessed++;
                
                String[] row = new String[fieldsToExport.size()];
                
                for (int i = 0; i < fieldsToExport.size(); i++) {
                    String fieldName = fieldsToExport.get(i);
                    
                    if (multiValueFieldNames.contains(fieldName)) {
                        // Handle multi-value field - join with comma
                        row[i] = getDelimitedValue(doc, fieldName);
                    } else {
                        // Handle regular field
                        row[i] = getFieldValueAsString(doc, fieldName);
                    }
                }
                
                writer.writeNext(row);
                
                if (documentsProcessed % 1000 == 0) {
                    logger.info("Processed {} documents", documentsProcessed);
                }
            }
        }
        
        logger.info("Delimited export complete. Processed {} documents", documentsProcessed);
    }
    
    private List<Map<String, String>> generateCombinations(Document doc, List<String> multiValueFieldNames) {
        List<Map<String, String>> combinations = new ArrayList<>();
        
        // Get all non-empty multi-value fields
        Map<String, List<String>> multiValues = new HashMap<>();
        for (String fieldName : multiValueFieldNames) {
            List<String> values = getMultiValues(doc, fieldName);
            if (!values.isEmpty()) {
                multiValues.put(fieldName, values);
            }
        }
        
        if (multiValues.isEmpty()) {
            return combinations;
        }
        
        // Generate all combinations
        generateCombinationsRecursive(multiValues, new ArrayList<>(multiValues.keySet()), 
                                    0, new HashMap<>(), combinations);
        
        return combinations;
    }
    
    private void generateCombinationsRecursive(Map<String, List<String>> multiValues,
                                             List<String> fieldNames,
                                             int index,
                                             Map<String, String> current,
                                             List<Map<String, String>> result) {
        if (index == fieldNames.size()) {
            result.add(new HashMap<>(current));
            return;
        }
        
        String fieldName = fieldNames.get(index);
        for (String value : multiValues.get(fieldName)) {
            current.put(fieldName, value);
            generateCombinationsRecursive(multiValues, fieldNames, index + 1, current, result);
        }
    }
    
    private List<String> getMultiValues(Document doc, String fieldName) {
        Object value = doc.get(fieldName);
        
        if (value == null) {
            return Collections.emptyList();
        }
        
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        
        // If it's not a list, treat as single value
        return Collections.singletonList(value.toString());
    }
    
    private String getDelimitedValue(Document doc, String fieldName) {
        List<String> values = getMultiValues(doc, fieldName);
        
        if (values.isEmpty()) {
            return "";
        }
        
        // Sort values for consistency
        Collections.sort(values);
        
        // Join with comma
        return String.join(",", values);
    }
    
    private String[] extractRow(Document doc, List<String> fieldsToExport, Map<String, String> multiValueOverrides) {
        String[] row = new String[fieldsToExport.size()];
        
        for (int i = 0; i < fieldsToExport.size(); i++) {
            String fieldName = fieldsToExport.get(i);
            
            if (multiValueOverrides.containsKey(fieldName)) {
                row[i] = multiValueOverrides.get(fieldName);
            } else {
                row[i] = getFieldValueAsString(doc, fieldName);
            }
        }
        
        return row;
    }
    
    private String getFieldValueAsString(Document doc, String fieldName) {
        // Handle nested fields (e.g., "address.city")
        String[] parts = fieldName.split("\\.");
        Object value = doc;
        
        for (String part : parts) {
            if (value instanceof Document) {
                value = ((Document) value).get(part);
            } else {
                value = null;
                break;
            }
        }
        
        if (value == null) {
            return "";
        }
        
        return value.toString();
    }
    
    private String generateFileName(String collectionName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String strategy = config.getExportStrategy().toString().toLowerCase();
        return String.format("%s_%s_%s.csv", collectionName, strategy, timestamp);
    }
    
    public void close() {
        if (mongoClient != null) {
            try {
                mongoClient.close();
                logger.info("MongoDB connection closed");
            } catch (Exception e) {
                logger.error("Error closing MongoDB connection", e);
            }
        }
    }
}