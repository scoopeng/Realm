package com.example.mongoexport.discovery;

import com.example.mongoexport.RelationExpander;
import com.example.mongoexport.config.DiscoveryConfiguration;
import com.example.mongoexport.config.FieldConfiguration;
import com.example.mongoexport.FieldNameMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service responsible for discovering all fields in a MongoDB collection,
 * including expanded relationships, and generating a configuration file.
 */
public class FieldDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(FieldDiscoveryService.class);
    
    private final MongoDatabase database;
    private final String collectionName;
    private final int sampleSize;
    private final int expansionDepth;
    private final int minDistinctNonNullValues;
    
    // Discovery tracking
    private final Map<String, FieldMetadata> fieldMetadataMap = new ConcurrentHashMap<>();
    private final Map<String, String> fieldToCollectionMap = new ConcurrentHashMap<>();
    private final Set<String> cachedCollections = new HashSet<>();
    private final Map<String, Map<ObjectId, Document>> collectionCache = new HashMap<>();
    
    // Track array internals separately - we'll consolidate them later
    private final Map<String, ArrayInternals> arrayInternalsMap = new HashMap<>();
    
    // Business IDs that should always be included
    private static final Set<String> BUSINESS_IDS = new HashSet<>(Arrays.asList(
        "mlsNumber", "listingId", "transactionId", "orderId", "contractNumber",
        "referenceNumber", "confirmationNumber", "invoiceNumber", "accountNumber",
        "caseNumber", "ticketId", "agentId"
    ));
    
    /**
     * Internal class to track field metadata during discovery
     */
    private static class FieldMetadata {
        String fieldPath;
        String sourceCollection;
        String dataType;
        Set<String> sampleValues = new HashSet<>();
        int nullCount = 0;
        int totalOccurrences = 0;
        List<Integer> arrayLengths = new ArrayList<>();
        String relationshipTarget;
        Map<String, FieldMetadata> nestedFields = new HashMap<>();
        String arrayObjectType;
        
        double getAvgArrayLength() {
            if (arrayLengths.isEmpty()) return 0;
            return arrayLengths.stream().mapToInt(Integer::intValue).average().orElse(0);
        }
        
        int getMaxArrayLength() {
            return arrayLengths.stream().mapToInt(Integer::intValue).max().orElse(0);
        }
    }
    
    /**
     * Track internals of arrays to understand their structure
     */
    private static class ArrayInternals {
        String arrayPath;
        boolean hasDirectObjectIds = false;
        String objectIdField = null; // For arrays of objects, which field contains ObjectId
        Set<String> internalFields = new HashSet<>();
        String targetCollection = null;
        Set<ObjectId> sampleIds = new HashSet<>();
    }
    
    public FieldDiscoveryService(MongoDatabase database, String collectionName) {
        this(database, collectionName, 10000, 3, 2);
    }
    
    public FieldDiscoveryService(MongoDatabase database, String collectionName,
                                 int sampleSize, int expansionDepth, int minDistinctNonNullValues) {
        this.database = database;
        this.collectionName = collectionName;
        this.sampleSize = sampleSize;
        this.expansionDepth = expansionDepth;
        this.minDistinctNonNullValues = minDistinctNonNullValues;
    }
    
    /**
     * Main discovery method that returns a complete configuration
     */
    public DiscoveryConfiguration discover() {
        logger.info("=== FIELD DISCOVERY SERVICE ===");
        logger.info("Collection: {}", collectionName);
        logger.info("Sample size: {}", sampleSize);
        logger.info("Expansion depth: {}", expansionDepth);
        
        // Create configuration
        DiscoveryConfiguration config = new DiscoveryConfiguration(collectionName);
        config.getDiscoveryParameters().setSampleSize(sampleSize);
        config.getDiscoveryParameters().setExpansionDepth(expansionDepth);
        config.getDiscoveryParameters().setMinDistinctNonNullValues(minDistinctNonNullValues);
        
        // Phase 1: Discover base fields
        logger.info("\n=== Phase 1: Discovering base fields ===");
        discoverBaseFields();
        
        // Phase 2: Analyze array internals and discover relationships
        logger.info("\n=== Phase 2: Analyzing arrays and discovering relationships ===");
        analyzeArraysAndDiscoverRelationships();
        
        // Phase 3: Discover expanded fields using cached data
        logger.info("\n=== Phase 3: Discovering expanded fields ===");
        discoverExpandedFields();
        
        // Phase 4: Build configuration from metadata
        logger.info("\n=== Phase 4: Building configuration ===");
        buildConfiguration(config);
        
        // Phase 5: Apply filtering rules
        logger.info("\n=== Phase 5: Applying filtering rules ===");
        applyFilteringRules(config);
        
        logger.info("Discovery complete: {} fields discovered, {} included for export",
            config.getFields().size(),
            config.getIncludedFields().size());
        
        return config;
    }
    
    /**
     * Phase 1: Discover base fields in the collection
     */
    private void discoverBaseFields() {
        MongoCollection<Document> collection = database.getCollection(collectionName);
        long totalDocs = collection.estimatedDocumentCount();
        logger.info("Scanning {} documents from {} (total: {})", 
            Math.min(sampleSize, totalDocs), collectionName, totalDocs);
        
        int scanned = 0;
        try (MongoCursor<Document> cursor = collection.find().limit(sampleSize).iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                discoverFieldsInDocument(doc, "", collectionName, 0);
                scanned++;
                
                if (scanned % 1000 == 0) {
                    logger.info("  Scanned {} documents, found {} unique fields", 
                        scanned, fieldMetadataMap.size());
                }
            }
        }
        
        logger.info("Base field discovery complete: {} fields found", fieldMetadataMap.size());
    }
    
    /**
     * Recursively discover fields in a document
     */
    private void discoverFieldsInDocument(Object value, String prefix, String sourceCollection, int depth) {
        if (depth > expansionDepth) return;
        
        if (value instanceof Document) {
            Document doc = (Document) value;
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                String fieldName = entry.getKey();
                String fieldPath = prefix.isEmpty() ? fieldName : prefix + "." + fieldName;
                Object fieldValue = entry.getValue();
                
                // Skip array internals - we'll handle them separately
                if (fieldPath.contains("[]")) {
                    trackArrayInternal(fieldPath, fieldName, fieldValue);
                    continue;
                }
                
                // Get or create metadata
                FieldMetadata metadata = fieldMetadataMap.computeIfAbsent(fieldPath, k -> {
                    FieldMetadata m = new FieldMetadata();
                    m.fieldPath = fieldPath;
                    m.sourceCollection = sourceCollection;
                    return m;
                });
                
                metadata.totalOccurrences++;
                fieldToCollectionMap.put(fieldPath, sourceCollection);
                
                // Process field value
                if (fieldValue == null || isEmptyValue(fieldValue)) {
                    metadata.nullCount++;
                } else if (fieldValue instanceof Document) {
                    metadata.dataType = "object";
                    discoverFieldsInDocument(fieldValue, fieldPath, sourceCollection, depth + 1);
                } else if (fieldValue instanceof List) {
                    metadata.dataType = "array";
                    List<?> list = (List<?>) fieldValue;
                    metadata.arrayLengths.add(list.size());
                    
                    if (!list.isEmpty()) {
                        analyzeArrayContents(fieldPath, list);
                    }
                } else if (fieldValue instanceof ObjectId) {
                    metadata.dataType = "objectId";
                    metadata.relationshipTarget = guessCollectionFromFieldName(fieldName);
                    trackSampleValue(metadata, fieldValue.toString());
                } else {
                    metadata.dataType = fieldValue.getClass().getSimpleName().toLowerCase();
                    trackSampleValue(metadata, fieldValue.toString());
                }
            }
        }
    }
    
    /**
     * Analyze the contents of an array to understand its structure
     */
    private void analyzeArrayContents(String arrayPath, List<?> list) {
        ArrayInternals internals = arrayInternalsMap.computeIfAbsent(arrayPath, k -> {
            ArrayInternals ai = new ArrayInternals();
            ai.arrayPath = arrayPath;
            return ai;
        });
        
        Object firstItem = list.get(0);
        FieldMetadata arrayMeta = fieldMetadataMap.get(arrayPath);
        
        if (firstItem instanceof ObjectId) {
            // Pattern 1: Direct array of ObjectIds
            internals.hasDirectObjectIds = true;
            internals.targetCollection = guessCollectionFromFieldName(getLastSegment(arrayPath));
            arrayMeta.arrayObjectType = "objectId";
            
            // Collect sample IDs
            for (Object item : list) {
                if (item instanceof ObjectId && internals.sampleIds.size() < 10) {
                    internals.sampleIds.add((ObjectId) item);
                }
            }
        } else if (firstItem instanceof Document) {
            // Pattern 2: Array of objects (might contain ObjectId fields)
            arrayMeta.arrayObjectType = "object";
            Document sampleDoc = (Document) firstItem;
            
            // Analyze the structure of objects in the array
            for (Map.Entry<String, Object> entry : sampleDoc.entrySet()) {
                String fieldName = entry.getKey();
                Object fieldValue = entry.getValue();
                internals.internalFields.add(fieldName);
                
                // Track this in the array internal path for now
                String internalPath = arrayPath + "[]." + fieldName;
                trackArrayInternal(internalPath, fieldName, fieldValue);
                
                // Check if this field is an ObjectId reference
                if (fieldValue instanceof ObjectId) {
                    if (internals.objectIdField == null || isLikelyReferenceField(fieldName)) {
                        internals.objectIdField = fieldName;
                        internals.targetCollection = guessCollectionFromFieldName(fieldName);
                        
                        // Collect sample IDs from array
                        for (Object item : list) {
                            if (item instanceof Document) {
                                Object id = ((Document) item).get(fieldName);
                                if (id instanceof ObjectId && internals.sampleIds.size() < 10) {
                                    internals.sampleIds.add((ObjectId) id);
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Simple array of primitives
            arrayMeta.arrayObjectType = firstItem.getClass().getSimpleName().toLowerCase();
        }
    }
    
    /**
     * Track internal fields of arrays (the [] paths)
     */
    private void trackArrayInternal(String internalPath, String fieldName, Object value) {
        // Extract the parent array path
        int bracketIndex = internalPath.indexOf("[]");
        if (bracketIndex > 0) {
            String arrayPath = internalPath.substring(0, bracketIndex);
            ArrayInternals internals = arrayInternalsMap.computeIfAbsent(arrayPath, k -> {
                ArrayInternals ai = new ArrayInternals();
                ai.arrayPath = arrayPath;
                return ai;
            });
            
            String internalField = internalPath.substring(bracketIndex + 3); // Skip "[]."
            if (!internalField.isEmpty()) {
                internals.internalFields.add(internalField);
            }
        }
    }
    
    /**
     * Check if a field name likely represents a reference
     */
    private boolean isLikelyReferenceField(String fieldName) {
        String lower = fieldName.toLowerCase();
        return !lower.contains("modified") && !lower.contains("created") && 
               !lower.contains("updated") && !lower.contains("deleted");
    }
    
    /**
     * Track sample values for statistics
     */
    private void trackSampleValue(FieldMetadata metadata, String value) {
        if (value != null && value.length() <= 200 && metadata.sampleValues.size() < 100) {
            metadata.sampleValues.add(value);
        }
    }
    
    /**
     * Check if a value is considered empty
     */
    private boolean isEmptyValue(Object value) {
        if (value == null) return true;
        if (value instanceof String) {
            String str = (String) value;
            return str.trim().isEmpty() || "null".equalsIgnoreCase(str);
        }
        if (value instanceof List) {
            return ((List<?>) value).isEmpty();
        }
        return false;
    }
    
    /**
     * Phase 2: Analyze arrays and discover relationships
     */
    private void analyzeArraysAndDiscoverRelationships() {
        logger.info("Analyzing {} arrays for references", arrayInternalsMap.size());
        
        // Process arrays that contain references
        for (ArrayInternals internals : arrayInternalsMap.values()) {
            if (internals.targetCollection != null && !internals.sampleIds.isEmpty()) {
                logger.info("  Array {} references {} collection", 
                    internals.arrayPath, internals.targetCollection);
                
                // Cache the target collection and discover its fields
                cacheAndAnalyzeCollection(internals.targetCollection, internals.sampleIds);
            }
        }
        
        // Also handle direct ObjectId fields (non-array references)
        Set<String> objectIdFields = fieldMetadataMap.entrySet().stream()
            .filter(e -> "objectId".equals(e.getValue().dataType))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
        
        logger.info("Found {} direct ObjectId reference fields", objectIdFields.size());
        
        for (String fieldPath : objectIdFields) {
            FieldMetadata metadata = fieldMetadataMap.get(fieldPath);
            if (metadata.relationshipTarget != null) {
                logger.info("  {} -> {}", fieldPath, metadata.relationshipTarget);
                cacheCollectionIfNeeded(metadata.relationshipTarget);
            }
        }
        
        logger.info("Cached {} collections for relationships", cachedCollections.size());
    }
    
    /**
     * Cache a collection and analyze its structure for array references
     */
    private void cacheAndAnalyzeCollection(String collectionName, Set<ObjectId> sampleIds) {
        if (cachedCollections.contains(collectionName)) {
            return;
        }
        
        // Check if collection exists
        boolean exists = database.listCollectionNames()
            .into(new ArrayList<>())
            .contains(collectionName);
        
        if (!exists) {
            logger.warn("Collection {} does not exist, skipping", collectionName);
            return;
        }
        
        MongoCollection<Document> collection = database.getCollection(collectionName);
        long count = collection.estimatedDocumentCount();
        
        if (count <= 100000) {
            logger.info("Caching and analyzing collection {} ({} documents)", collectionName, count);
            Map<ObjectId, Document> cache = new HashMap<>();
            
            try (MongoCursor<Document> cursor = collection.find().iterator()) {
                while (cursor.hasNext()) {
                    Document doc = cursor.next();
                    Object id = doc.get("_id");
                    if (id instanceof ObjectId) {
                        cache.put((ObjectId) id, doc);
                    }
                }
            }
            
            collectionCache.put(collectionName, cache);
            cachedCollections.add(collectionName);
            
            // Analyze sample documents to find best display field
            if (!sampleIds.isEmpty()) {
                String bestField = findBestDisplayField(collectionName, cache, sampleIds);
                logger.info("  Best display field for {}: {}", collectionName, bestField);
                
                // Store this information for later use
                for (ArrayInternals internals : arrayInternalsMap.values()) {
                    if (collectionName.equals(internals.targetCollection)) {
                        // This will be used when building configuration
                    }
                }
            }
            
            logger.info("  Cached {} documents from {}", cache.size(), collectionName);
        } else {
            logger.info("Collection {} too large ({} docs), will use lazy loading", collectionName, count);
            cachedCollections.add(collectionName);
        }
    }
    
    /**
     * Find the best field to display from a referenced collection
     */
    private String findBestDisplayField(String collectionName, Map<ObjectId, Document> cache, Set<ObjectId> sampleIds) {
        if (cache.isEmpty()) return null;
        
        // Get sample documents
        List<Document> samples = sampleIds.stream()
            .map(cache::get)
            .filter(Objects::nonNull)
            .limit(10)
            .collect(Collectors.toList());
        
        if (samples.isEmpty() && !cache.isEmpty()) {
            samples = cache.values().stream().limit(10).collect(Collectors.toList());
        }
        
        if (samples.isEmpty()) return null;
        
        // Analyze fields in sample documents
        Map<String, Integer> fieldCounts = new HashMap<>();
        Map<String, String> fieldTypes = new HashMap<>();
        
        for (Document doc : samples) {
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                
                if (value != null && !isEmptyValue(value)) {
                    fieldCounts.merge(field, 1, Integer::sum);
                    if (!fieldTypes.containsKey(field) && value instanceof String) {
                        fieldTypes.put(field, "string");
                    }
                }
            }
        }
        
        // Prefer certain field names
        List<String> preferredNames = Arrays.asList("name", "title", "description", "value", "label", "text");
        
        for (String preferred : preferredNames) {
            if (fieldTypes.containsKey(preferred) && "string".equals(fieldTypes.get(preferred))) {
                return preferred;
            }
        }
        
        // Return first non-ID string field
        return fieldTypes.entrySet().stream()
            .filter(e -> "string".equals(e.getValue()))
            .filter(e -> !e.getKey().endsWith("Id") && !e.getKey().equals("_id"))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Cache a collection for later use
     */
    private void cacheCollectionIfNeeded(String collectionName) {
        cacheAndAnalyzeCollection(collectionName, new HashSet<>());
    }
    
    /**
     * Phase 3: Discover expanded fields from relationships
     */
    private void discoverExpandedFields() {
        // Use RelationExpander to discover expanded field structure
        RelationExpander expander = new RelationExpander(database);
        
        // Sample some documents to discover expanded structure
        MongoCollection<Document> collection = database.getCollection(collectionName);
        int sampleCount = Math.min(100, sampleSize / 10);
        
        logger.info("Sampling {} documents to discover expanded field structure", sampleCount);
        
        try (MongoCursor<Document> cursor = collection.find().limit(sampleCount).iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                Document expanded = expander.expandDocument(doc, collectionName, 1);
                
                // Discover fields in expanded document
                discoverFieldsInDocument(expanded, "", collectionName, 0);
            }
        }
        
        logger.info("Expanded field discovery complete");
    }
    
    /**
     * Phase 4: Build configuration from metadata
     */
    private void buildConfiguration(DiscoveryConfiguration config) {
        Set<String> processedArrays = new HashSet<>();
        
        for (Map.Entry<String, FieldMetadata> entry : fieldMetadataMap.entrySet()) {
            FieldMetadata metadata = entry.getValue();
            String fieldPath = metadata.fieldPath;
            
            // Skip array internals - they'll be handled with their parent array
            if (fieldPath.contains("[]")) {
                continue;
            }
            
            FieldConfiguration fieldConfig = new FieldConfiguration(fieldPath);
            
            // Set basic properties
            fieldConfig.setSourceCollection(metadata.sourceCollection);
            fieldConfig.setDataType(metadata.dataType);
            fieldConfig.setBusinessName(FieldNameMapper.getBusinessName(fieldPath));
            fieldConfig.setRelationshipTarget(metadata.relationshipTarget);
            
            // Set statistics
            FieldConfiguration.FieldStatistics stats = new FieldConfiguration.FieldStatistics();
            stats.setDistinctNonNullValues(metadata.sampleValues.size());
            stats.setNullCount(metadata.nullCount);
            stats.setTotalOccurrences(metadata.totalOccurrences);
            
            if (!metadata.sampleValues.isEmpty()) {
                List<String> samples = new ArrayList<>(metadata.sampleValues);
                samples.sort(String::compareTo);
                stats.setSampleValues(samples.subList(0, Math.min(5, samples.size())));
            }
            
            // Handle arrays specially
            if ("array".equals(metadata.dataType)) {
                stats.setAvgArrayLength(metadata.getAvgArrayLength());
                stats.setMaxArrayLength(metadata.getMaxArrayLength());
                
                // Configure array handling based on array internals
                FieldConfiguration.ArrayConfiguration arrayConfig = configureArray(fieldPath, metadata);
                fieldConfig.setArrayConfig(arrayConfig);
                
                // Track required collection if this array references another collection
                if (arrayConfig.getReferenceCollection() != null) {
                    config.addRequiredCollection(arrayConfig.getReferenceCollection());
                }
            }
            
            fieldConfig.setStatistics(stats);
            config.getFields().add(fieldConfig);
            
            // Track required collections for direct references
            if (metadata.relationshipTarget != null) {
                config.addRequiredCollection(metadata.relationshipTarget);
            }
        }
        
        // Sort fields by path for consistent ordering
        config.getFields().sort(Comparator.comparing(FieldConfiguration::getFieldPath));
    }
    
    /**
     * Configure array based on its internal structure
     */
    private FieldConfiguration.ArrayConfiguration configureArray(String arrayPath, FieldMetadata metadata) {
        FieldConfiguration.ArrayConfiguration arrayConfig = new FieldConfiguration.ArrayConfiguration();
        arrayConfig.setObjectType(metadata.arrayObjectType);
        
        ArrayInternals internals = arrayInternalsMap.get(arrayPath);
        
        if (internals != null) {
            if (internals.hasDirectObjectIds && internals.targetCollection != null) {
                // Pattern 1: Direct array of ObjectIds
                arrayConfig.setReferenceCollection(internals.targetCollection);
                
                // Find best display field from cached collection
                Map<ObjectId, Document> cache = collectionCache.get(internals.targetCollection);
                if (cache != null) {
                    String displayField = findBestDisplayField(internals.targetCollection, cache, internals.sampleIds);
                    arrayConfig.setExtractField(displayField);
                }
            } else if (internals.objectIdField != null && internals.targetCollection != null) {
                // Pattern 2: Array of objects with ObjectId field
                arrayConfig.setReferenceField(internals.objectIdField);
                arrayConfig.setReferenceCollection(internals.targetCollection);
                
                // Find best display field from cached collection
                Map<ObjectId, Document> cache = collectionCache.get(internals.targetCollection);
                if (cache != null) {
                    String displayField = findBestDisplayField(internals.targetCollection, cache, internals.sampleIds);
                    arrayConfig.setExtractField(displayField);
                }
            } else if ("object".equals(metadata.arrayObjectType)) {
                // Array of objects without references - find best field to extract
                String extractField = findBestExtractFieldFromInternals(internals);
                arrayConfig.setExtractField(extractField);
            }
        } else if ("object".equals(metadata.arrayObjectType)) {
            // Fallback for arrays of objects
            String extractField = findBestExtractField(arrayPath);
            arrayConfig.setExtractField(extractField);
        }
        
        return arrayConfig;
    }
    
    /**
     * Find best field to extract from array objects using internals
     */
    private String findBestExtractFieldFromInternals(ArrayInternals internals) {
        List<String> preferredNames = Arrays.asList("name", "title", "description", "value", "date", "dateTime");
        
        for (String preferred : preferredNames) {
            if (internals.internalFields.contains(preferred)) {
                return preferred;
            }
        }
        
        // Return first non-ID field
        return internals.internalFields.stream()
            .filter(field -> !field.endsWith("Id") && !field.equals("_id"))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Find the best field to extract from array objects (fallback method)
     */
    private String findBestExtractField(String arrayFieldPath) {
        // This is the old method - kept as fallback
        return null;
    }
    
    /**
     * Phase 5: Apply filtering rules to determine which fields to include
     */
    private void applyFilteringRules(DiscoveryConfiguration config) {
        int excluded = 0;
        int businessIdsKept = 0;
        
        for (FieldConfiguration field : config.getFields()) {
            boolean shouldInclude = false;
            String fieldPath = field.getFieldPath();
            
            // Always include business IDs if they have any data
            if (isBusinessId(fieldPath)) {
                FieldConfiguration.FieldStatistics stats = field.getStatistics();
                if (stats != null && stats.getDistinctNonNullValues() > 0) {
                    shouldInclude = true;
                    businessIdsKept++;
                }
            }
            // Exclude technical IDs
            else if (isTechnicalId(fieldPath)) {
                shouldInclude = false;
            }
            // Apply minimum distinct values rule
            else {
                FieldConfiguration.FieldStatistics stats = field.getStatistics();
                if (stats != null) {
                    int distinctValues = stats.getDistinctNonNullValues() != null ? 
                        stats.getDistinctNonNullValues() : 0;
                    shouldInclude = distinctValues >= minDistinctNonNullValues;
                }
            }
            
            field.setInclude(shouldInclude);
            if (!shouldInclude) {
                excluded++;
            }
        }
        
        logger.info("Filtering complete: {} fields excluded, {} business IDs kept", 
            excluded, businessIdsKept);
    }
    
    /**
     * Check if a field is a business ID
     */
    private boolean isBusinessId(String fieldPath) {
        String lastPart = getLastSegment(fieldPath);
        return BUSINESS_IDS.contains(lastPart);
    }
    
    /**
     * Check if a field is a technical ID that should be excluded
     */
    private boolean isTechnicalId(String fieldPath) {
        String lastPart = getLastSegment(fieldPath);
        
        if ("_id".equals(lastPart) || "__v".equals(lastPart)) {
            return true;
        }
        
        // Exclude fields ending with Id unless they're business IDs
        if (lastPart.endsWith("Id") && !isBusinessId(fieldPath)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get the last segment of a field path
     */
    private String getLastSegment(String fieldPath) {
        return fieldPath.contains(".") ? 
            fieldPath.substring(fieldPath.lastIndexOf(".") + 1) : fieldPath;
    }
    
    /**
     * Ensure business IDs are discovered even if not in sample
     */
    private void ensureBusinessIdsDiscovered() {
        // Don't add fields artificially - they should be discovered from data
        // If they're not in the sample, they're probably not in this collection
    }
    
    /**
     * Guess collection name from field name
     */
    private String guessCollectionFromFieldName(String fieldName) {
        Map<String, String> mappings = new HashMap<>();
        mappings.put("property", "properties");
        mappings.put("listing", "listings");
        mappings.put("agent", "agents");
        mappings.put("listingagent", "agents");
        mappings.put("buyeragent", "agents");
        mappings.put("selleragent", "agents");
        mappings.put("person", "people");
        mappings.put("buyer", "people");
        mappings.put("seller", "people");
        mappings.put("brokerage", "brokerages");
        mappings.put("transaction", "transactions");
        mappings.put("openhouse", "openHouses");
        mappings.put("showing", "showings");
        mappings.put("lifestyle", "lifestyles");
        mappings.put("tag", "tags");
        
        String normalized = fieldName.toLowerCase().replace("id", "").replace("_", "");
        
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // Try pluralizing
        if (!normalized.endsWith("s")) {
            return normalized + "s";
        }
        
        return normalized;
    }
}