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

public class FullDetailExporter {
    private static final Logger logger = LoggerFactory.getLogger(FullDetailExporter.class);
    
    private final ExportConfig config;
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    
    public FullDetailExporter(ExportConfig config) {
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
    
    public void exportCollectionWithFullDetail(String collectionName) {
        logger.info("Starting full detail export of collection: {}", collectionName);
        
        try {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            long documentCount = collection.countDocuments();
            logger.info("Total documents in collection: {}", documentCount);
            
            if (documentCount == 0) {
                logger.warn("Collection {} is empty. Skipping export.", collectionName);
                return;
            }
            
            // First, discover all possible fields by sampling documents
            logger.info("Discovering fields by sampling documents...");
            Map<String, Class<?>> allFields = discoverAllFields(collection);
            logger.info("Discovered {} unique fields", allFields.size());
            
            // Sort fields for consistent column ordering
            List<String> sortedFields = new ArrayList<>(allFields.keySet());
            Collections.sort(sortedFields);
            
            String fileName = generateFileName(collectionName);
            Path filePath = Paths.get(config.getOutputDirectory(), fileName);
            
            // Use tab delimiter for MySQL bulk loading compatibility
            try (CSVWriter writer = new CSVWriter(new FileWriter(filePath.toString(), StandardCharsets.UTF_8), 
                    '\t', CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END)) {
                
                // Write header
                writer.writeNext(sortedFields.toArray(new String[0]));
                
                // Export all documents
                try (MongoCursor<Document> cursor = collection.find().cursor()) {
                    int count = 0;
                    while (cursor.hasNext()) {
                        Document doc = cursor.next();
                        String[] row = new String[sortedFields.size()];
                        
                        for (int i = 0; i < sortedFields.size(); i++) {
                            String fieldPath = sortedFields.get(i);
                            Object value = getNestedValue(doc, fieldPath);
                            row[i] = convertValueToString(value);
                        }
                        
                        writer.writeNext(row);
                        
                        count++;
                        if (count % 1000 == 0) {
                            logger.info("Processed {} documents", count);
                        }
                    }
                    
                    logger.info("Export completed. Total documents exported: {}", count);
                }
                
                logger.info("Export completed successfully. File written to: {}", filePath);
            }
            
        } catch (Exception e) {
            logger.error("Failed to export collection: {}", collectionName, e);
            throw new RuntimeException("Failed to export collection: " + collectionName, e);
        }
    }
    
    private Map<String, Class<?>> discoverAllFields(MongoCollection<Document> collection) {
        Map<String, Class<?>> allFields = new LinkedHashMap<>();
        
        // Sample documents to discover fields (sample more for better coverage)
        int sampleSize = 1000;
        List<Document> samples = collection.find().limit(sampleSize).into(new ArrayList<>());
        
        logger.info("Analyzing {} sample documents to discover fields...", samples.size());
        
        for (Document doc : samples) {
            Map<String, Class<?>> docFields = flattenDocument(doc, "");
            for (Map.Entry<String, Class<?>> entry : docFields.entrySet()) {
                allFields.put(entry.getKey(), entry.getValue());
            }
        }
        
        return allFields;
    }
    
    private Map<String, Class<?>> flattenDocument(Document doc, String prefix) {
        Map<String, Class<?>> fields = new LinkedHashMap<>();
        
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String fieldPath = prefix.isEmpty() ? key : prefix + "." + key;
            
            if (value instanceof Document) {
                // Recursively flatten nested documents
                Map<String, Class<?>> nestedFields = flattenDocument((Document) value, fieldPath);
                fields.putAll(nestedFields);
            } else if (value instanceof List) {
                // For lists, we'll store them as comma-separated values
                fields.put(fieldPath, List.class);
                
                // If it's a list of documents, also flatten the structure
                List<?> list = (List<?>) value;
                if (!list.isEmpty() && list.get(0) instanceof Document) {
                    // Sample the first document in the list to get structure
                    Document sampleDoc = (Document) list.get(0);
                    Map<String, Class<?>> listDocFields = flattenDocument(sampleDoc, fieldPath + "[0]");
                    fields.putAll(listDocFields);
                }
            } else {
                // Store the field and its type
                fields.put(fieldPath, value != null ? value.getClass() : String.class);
            }
        }
        
        return fields;
    }
    
    private Object getNestedValue(Document doc, String fieldPath) {
        String[] parts = fieldPath.split("\\.");
        Object current = doc;
        
        for (int i = 0; i < parts.length; i++) {
            if (current == null) {
                return null;
            }
            
            String part = parts[i];
            
            // Handle array notation like "field[0]"
            if (part.contains("[") && part.contains("]")) {
                String fieldName = part.substring(0, part.indexOf("["));
                int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));
                
                if (current instanceof Document) {
                    Object fieldValue = ((Document) current).get(fieldName);
                    if (fieldValue instanceof List) {
                        List<?> list = (List<?>) fieldValue;
                        if (index < list.size()) {
                            current = list.get(index);
                        } else {
                            return null;
                        }
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                // Regular field access
                if (current instanceof Document) {
                    current = ((Document) current).get(part);
                } else {
                    return null;
                }
            }
        }
        
        return current;
    }
    
    private String convertValueToString(Object value) {
        if (value == null) {
            return "";
        }
        
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            // Convert list to comma-separated string
            return list.stream()
                    .map(item -> {
                        if (item instanceof Document) {
                            // For embedded documents in lists, convert to JSON-like string
                            return item.toString();
                        } else {
                            return String.valueOf(item);
                        }
                    })
                    .collect(Collectors.joining(","));
        } else if (value instanceof Document) {
            // For embedded documents, convert to JSON-like string
            return value.toString();
        } else {
            return String.valueOf(value);
        }
    }
    
    private String generateFileName(String collectionName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("%s_fulldetail_%s.csv", collectionName, timestamp);
    }
    
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            logger.info("MongoDB connection closed");
        }
    }
}