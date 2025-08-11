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
public class FieldDiscoveryService
{
    private static final Logger logger = LoggerFactory.getLogger(FieldDiscoveryService.class);

    private final MongoDatabase database;
    private final String collectionName;
    private final int sampleSize;
    private final int expansionDepth;
    private final int minDistinctNonNullValues;
    private final double sparseFieldThreshold;  // Minimum percentage of non-null values (0.1 = 10%)

    // Discovery tracking
    private final Map<String, FieldMetadata> fieldMetadataMap = new ConcurrentHashMap<>();
    private final Map<String, String> fieldToCollectionMap = new ConcurrentHashMap<>();
    private final Set<String> cachedCollections = new HashSet<>();
    private Set<String> currentDocumentFields;  // Track fields in current document to avoid double-counting
    private final Map<String, Map<ObjectId, Document>> collectionCache = new HashMap<>();

    // Track array internals separately - we'll consolidate them later
    private final Map<String, ArrayInternals> arrayInternalsMap = new HashMap<>();

    // Track actual documents scanned for sparse field calculation
    private int actualDocumentsScanned = 0;

    // Business IDs that should always be included
    private static final Set<String> BUSINESS_IDS = new HashSet<>(Arrays.asList("mlsNumber", "listingId", "transactionId", "orderId", "contractNumber", "referenceNumber", "confirmationNumber", "invoiceNumber", "accountNumber", "caseNumber", "ticketId", "agentId"));

    /**
     * Internal class to track field metadata during discovery
     */
    private static class FieldMetadata
    {
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

        // Compound sparsity for expanded fields
        Double compoundSparsity;
        Integer adjustedOccurrences;

        double getAvgArrayLength()
        {
            if (arrayLengths.isEmpty()) return 0;
            return arrayLengths.stream().mapToInt(Integer::intValue).average().orElse(0);
        }

        int getMaxArrayLength()
        {
            return arrayLengths.stream().mapToInt(Integer::intValue).max().orElse(0);
        }
    }

    /**
     * Track internals of arrays to understand their structure
     */
    private static class ArrayInternals
    {
        String arrayPath;
        boolean hasDirectObjectIds = false;
        String objectIdField = null; // For arrays of objects, which field contains ObjectId
        Set<String> internalFields = new HashSet<>();
        String targetCollection = null;
        Set<ObjectId> sampleIds = new HashSet<>();
    }

    public FieldDiscoveryService(MongoDatabase database, String collectionName)
    {
        this(database, collectionName, 10000, 3, 2, 0.1);  // Default: 10% sparse threshold
    }

    public FieldDiscoveryService(MongoDatabase database, String collectionName, int sampleSize, int expansionDepth, int minDistinctNonNullValues)
    {
        this(database, collectionName, sampleSize, expansionDepth, minDistinctNonNullValues, 0.1);  // Default: 10% sparse threshold
    }

    public FieldDiscoveryService(MongoDatabase database, String collectionName, int sampleSize, int expansionDepth, int minDistinctNonNullValues, double sparseFieldThreshold)
    {
        this.database = database;
        this.collectionName = collectionName;
        this.sampleSize = sampleSize;
        this.expansionDepth = expansionDepth;
        this.minDistinctNonNullValues = minDistinctNonNullValues;
        this.sparseFieldThreshold = sparseFieldThreshold;
    }

    /**
     * Main discovery method that returns a complete configuration
     */
    public DiscoveryConfiguration discover()
    {
        logger.info("=== FIELD DISCOVERY SERVICE ===");
        logger.info("Collection: {}", collectionName);
        logger.info("Sample size: {}", sampleSize);
        logger.info("Expansion depth: {}", expansionDepth);
        logger.info("Sparse field threshold: {}% non-null required", sparseFieldThreshold * 100);

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

        logger.info("Discovery complete: {} fields discovered, {} included for export", config.getFields().size(), config.getIncludedFields().size());

        // Generate audit tree file
        generateAuditTree(config);

        return config;
    }

    /**
     * Phase 1: Discover base fields in the collection
     */
    private void discoverBaseFields()
    {
        MongoCollection<Document> collection = database.getCollection(collectionName);
        long totalDocs = collection.estimatedDocumentCount();
        logger.info("Scanning {} documents from {} (total: {})", Math.min(sampleSize, totalDocs), collectionName, totalDocs);

        int scanned = 0;
        try (MongoCursor<Document> cursor = collection.find().limit(sampleSize).iterator())
        {
            while (cursor.hasNext())
            {
                Document doc = cursor.next();
                // Track fields seen in this document to avoid double-counting
                currentDocumentFields = new HashSet<>();
                discoverFieldsInDocument(doc, "", collectionName, 0);
                scanned++;

                if (scanned % 1000 == 0)
                {
                    logger.info("  Scanned {} documents, found {} unique fields", scanned, fieldMetadataMap.size());
                }
            }
        }

        // Store the actual number of documents scanned
        actualDocumentsScanned = scanned;

        logger.info("Base field discovery complete: {} fields found from {} documents", fieldMetadataMap.size(), scanned);
    }

    /**
     * Recursively discover fields in a document
     */
    private void discoverFieldsInDocument(Object value, String prefix, String sourceCollection, int depth)
    {
        if (depth > expansionDepth) return;

        if (value instanceof Document)
        {
            Document doc = (Document) value;
            for (Map.Entry<String, Object> entry : doc.entrySet())
            {
                String fieldName = entry.getKey();
                String fieldPath = prefix.isEmpty() ? fieldName : prefix + "." + fieldName;
                Object fieldValue = entry.getValue();

                // Skip array internals - we'll handle them separately
                if (fieldPath.contains("[]"))
                {
                    trackArrayInternal(fieldPath, fieldName, fieldValue);
                    continue;
                }

                // Get or create metadata
                FieldMetadata metadata = fieldMetadataMap.computeIfAbsent(fieldPath, k ->
                {
                    FieldMetadata m = new FieldMetadata();
                    m.fieldPath = fieldPath;
                    m.sourceCollection = sourceCollection;
                    return m;
                });

                // Check if value is non-empty before counting occurrence
                boolean hasValue = fieldValue != null && !isEmptyValue(fieldValue);
                
                // Only count this field once per document AND only if it has a value
                if (currentDocumentFields != null && !currentDocumentFields.contains(fieldPath) && hasValue)
                {
                    metadata.totalOccurrences++;
                    currentDocumentFields.add(fieldPath);
                    
                    // Debug logging for specific fields
                    if (fieldPath.equals("fees") || fieldPath.equals("viewTypes") || fieldPath.equals("belowGradeAreaFinished"))
                    {
                        logger.debug("Field {} occurrence count: {}", fieldPath, metadata.totalOccurrences);
                    }
                }
                fieldToCollectionMap.put(fieldPath, sourceCollection);

                // Process field value
                if (fieldValue == null || isEmptyValue(fieldValue))
                {
                    metadata.nullCount++;
                } else if (fieldValue instanceof Document)
                {
                    metadata.dataType = "object";
                    discoverFieldsInDocument(fieldValue, fieldPath, sourceCollection, depth + 1);
                } else if (fieldValue instanceof List)
                {
                    metadata.dataType = "array";
                    List<?> list = (List<?>) fieldValue;
                    metadata.arrayLengths.add(list.size());

                    if (!list.isEmpty())
                    {
                        analyzeArrayContents(fieldPath, list);
                    }
                } else if (fieldValue instanceof ObjectId)
                {
                    metadata.dataType = "objectId";
                    metadata.relationshipTarget = guessCollectionFromFieldName(fieldName);
                    trackSampleValue(metadata, fieldValue.toString());
                } else
                {
                    metadata.dataType = fieldValue.getClass().getSimpleName().toLowerCase();
                    trackSampleValue(metadata, fieldValue.toString());
                }
            }
        }
    }

    /**
     * Analyze the contents of an array to understand its structure
     */
    private void analyzeArrayContents(String arrayPath, List<?> list)
    {
        ArrayInternals internals = arrayInternalsMap.computeIfAbsent(arrayPath, k ->
        {
            ArrayInternals ai = new ArrayInternals();
            ai.arrayPath = arrayPath;
            return ai;
        });

        Object firstItem = list.get(0);
        FieldMetadata arrayMeta = fieldMetadataMap.get(arrayPath);

        if (firstItem instanceof ObjectId)
        {
            // Pattern 1: Direct array of ObjectIds
            internals.hasDirectObjectIds = true;
            internals.targetCollection = guessCollectionFromFieldName(getLastSegment(arrayPath));
            arrayMeta.arrayObjectType = "objectId";

            // Collect sample IDs
            for (Object item : list)
            {
                if (item instanceof ObjectId)
                {
                    if (internals.sampleIds.size() < 10)
                    {
                        internals.sampleIds.add((ObjectId) item);
                    }
                    // Track this ID as a sample value for statistics
                    trackSampleValue(arrayMeta, item.toString());
                }
            }
        } else if (firstItem instanceof Document)
        {
            // Pattern 2: Array of objects (might contain ObjectId fields)
            arrayMeta.arrayObjectType = "object";
            Document sampleDoc = (Document) firstItem;

            // Analyze the structure of objects in the array
            boolean hasTrackedValues = false;
            for (Map.Entry<String, Object> entry : sampleDoc.entrySet())
            {
                String fieldName = entry.getKey();
                Object fieldValue = entry.getValue();
                internals.internalFields.add(fieldName);

                // Track this in the array internal path for now
                String internalPath = arrayPath + "[]." + fieldName;
                trackArrayInternal(internalPath, fieldName, fieldValue);

                // Check if this field is an ObjectId reference
                if (fieldValue instanceof ObjectId)
                {
                    if (internals.objectIdField == null || isLikelyReferenceField(fieldName))
                    {
                        internals.objectIdField = fieldName;
                        internals.targetCollection = guessCollectionFromFieldName(fieldName);

                        // Collect sample IDs from array
                        for (Object item : list)
                        {
                            if (item instanceof Document)
                            {
                                Object id = ((Document) item).get(fieldName);
                                if (id instanceof ObjectId)
                                {
                                    if (internals.sampleIds.size() < 10)
                                    {
                                        internals.sampleIds.add((ObjectId) id);
                                    }
                                    // Track this ID as a sample value for statistics
                                    trackSampleValue(arrayMeta, id.toString());
                                    hasTrackedValues = true;
                                }
                            }
                        }
                    }
                }
            }

            // If we haven't tracked any values yet (no ObjectId fields), track the documents themselves
            if (!hasTrackedValues)
            {
                for (Object item : list)
                {
                    if (item instanceof Document)
                    {
                        // Track a hash or summary of the document
                        trackSampleValue(arrayMeta, item.toString());
                    }
                }
            }
        } else
        {
            // Simple array of primitives
            arrayMeta.arrayObjectType = firstItem.getClass().getSimpleName().toLowerCase();

            // Track sample values for simple arrays
            for (Object item : list)
            {
                if (item != null)
                {
                    trackSampleValue(arrayMeta, item.toString());
                }
            }
        }
    }

    /**
     * Track internal fields of arrays (the [] paths)
     */
    private void trackArrayInternal(String internalPath, String fieldName, Object value)
    {
        // Extract the parent array path
        int bracketIndex = internalPath.indexOf("[]");
        if (bracketIndex > 0)
        {
            String arrayPath = internalPath.substring(0, bracketIndex);
            ArrayInternals internals = arrayInternalsMap.computeIfAbsent(arrayPath, k ->
            {
                ArrayInternals ai = new ArrayInternals();
                ai.arrayPath = arrayPath;
                return ai;
            });

            String internalField = internalPath.substring(bracketIndex + 3); // Skip "[]."
            if (!internalField.isEmpty())
            {
                internals.internalFields.add(internalField);
            }
        }
    }

    /**
     * Check if a field name likely represents a reference
     */
    private boolean isLikelyReferenceField(String fieldName)
    {
        String lower = fieldName.toLowerCase();
        return !lower.contains("modified") && !lower.contains("created") && !lower.contains("updated") && !lower.contains("deleted");
    }

    /**
     * Track sample values for statistics
     */
    private void trackSampleValue(FieldMetadata metadata, String value)
    {
        if (value != null && value.length() <= 200 && metadata.sampleValues.size() < 100)
        {
            metadata.sampleValues.add(value);
        }
    }

    /**
     * Check if a value is considered empty
     */
    private boolean isEmptyValue(Object value)
    {
        if (value == null) return true;
        if (value instanceof String)
        {
            String str = (String) value;
            return str.trim().isEmpty() || "null".equalsIgnoreCase(str);
        }
        if (value instanceof List)
        {
            return ((List<?>) value).isEmpty();
        }
        return false;
    }

    /**
     * Phase 2: Analyze arrays and discover relationships
     */
    private void analyzeArraysAndDiscoverRelationships()
    {
        logger.info("Analyzing {} arrays for references", arrayInternalsMap.size());

        // Process arrays that contain references
        for (ArrayInternals internals : arrayInternalsMap.values())
        {
            if (internals.targetCollection != null && !internals.sampleIds.isEmpty())
            {
                logger.info("  Array {} references {} collection", internals.arrayPath, internals.targetCollection);

                // Cache the target collection and discover its fields
                cacheAndAnalyzeCollection(internals.targetCollection, internals.sampleIds);
            }
        }

        // Also handle direct ObjectId fields (non-array references)
        Set<String> objectIdFields = fieldMetadataMap.entrySet().stream().filter(e -> "objectId".equals(e.getValue().dataType)).map(Map.Entry::getKey).collect(Collectors.toSet());

        logger.info("Found {} direct ObjectId reference fields", objectIdFields.size());

        for (String fieldPath : objectIdFields)
        {
            FieldMetadata metadata = fieldMetadataMap.get(fieldPath);
            if (metadata.relationshipTarget != null)
            {
                logger.info("  {} -> {}", fieldPath, metadata.relationshipTarget);
                cacheCollectionIfNeeded(metadata.relationshipTarget);
            }
        }

        logger.info("Cached {} collections for relationships", cachedCollections.size());
    }

    /**
     * Cache a collection and analyze its structure for array references
     */
    private void cacheAndAnalyzeCollection(String collectionName, Set<ObjectId> sampleIds)
    {
        if (cachedCollections.contains(collectionName))
        {
            return;
        }

        // Check if collection exists
        boolean exists = database.listCollectionNames().into(new ArrayList<>()).contains(collectionName);

        if (!exists)
        {
            logger.warn("Collection {} does not exist, skipping", collectionName);
            return;
        }

        MongoCollection<Document> collection = database.getCollection(collectionName);
        long count = collection.estimatedDocumentCount();

        if (count <= 100000)
        {
            logger.info("Caching and analyzing collection {} ({} documents)", collectionName, count);
            Map<ObjectId, Document> cache = new HashMap<>();

            try (MongoCursor<Document> cursor = collection.find().iterator())
            {
                while (cursor.hasNext())
                {
                    Document doc = cursor.next();
                    Object id = doc.get("_id");
                    if (id instanceof ObjectId)
                    {
                        cache.put((ObjectId) id, doc);
                    }
                }
            }

            collectionCache.put(collectionName, cache);
            cachedCollections.add(collectionName);

            // Analyze sample documents to find best display field
            if (!sampleIds.isEmpty())
            {
                String bestField = findBestDisplayField(collectionName, cache, sampleIds);
                logger.info("  Best display field for {}: {}", collectionName, bestField);

                // Store this information for later use
                for (ArrayInternals internals : arrayInternalsMap.values())
                {
                    if (collectionName.equals(internals.targetCollection))
                    {
                        // This will be used when building configuration
                    }
                }
            }

            logger.info("  Cached {} documents from {}", cache.size(), collectionName);
        } else
        {
            logger.info("Collection {} too large ({} docs), will use lazy loading", collectionName, count);
            cachedCollections.add(collectionName);
        }
    }

    /**
     * Find the best field to display from a referenced collection
     */
    private String findBestDisplayField(String collectionName, Map<ObjectId, Document> cache, Set<ObjectId> sampleIds)
    {
        if (cache.isEmpty()) return null;

        // Get sample documents
        List<Document> samples = sampleIds.stream().map(cache::get).filter(Objects::nonNull).limit(10).collect(Collectors.toList());

        if (samples.isEmpty() && !cache.isEmpty())
        {
            samples = cache.values().stream().limit(10).collect(Collectors.toList());
        }

        if (samples.isEmpty()) return null;

        // Analyze fields in sample documents
        Map<String, Integer> fieldCounts = new HashMap<>();
        Map<String, String> fieldTypes = new HashMap<>();

        for (Document doc : samples)
        {
            for (Map.Entry<String, Object> entry : doc.entrySet())
            {
                String field = entry.getKey();
                Object value = entry.getValue();

                if (value != null && !isEmptyValue(value))
                {
                    fieldCounts.merge(field, 1, Integer::sum);
                    if (!fieldTypes.containsKey(field) && value instanceof String)
                    {
                        fieldTypes.put(field, "string");
                    }
                }
            }
        }

        // Prefer certain field names - UPDATED ORDER for better defaults
        List<String> preferredNames = Arrays.asList("name", "fullName", "displayName", "title", "description", "value", "label", "text");

        for (String preferred : preferredNames)
        {
            if (fieldTypes.containsKey(preferred) && "string".equals(fieldTypes.get(preferred)))
            {
                return preferred;
            }
        }

        // Return first non-ID string field
        return fieldTypes.entrySet().stream().filter(e -> "string".equals(e.getValue())).filter(e -> !e.getKey().endsWith("Id") && !e.getKey().equals("_id")).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    /**
     * Get all available string fields from a referenced collection
     */
    private List<String> getAvailableFields(String collectionName, Map<ObjectId, Document> cache)
    {
        if (cache == null || cache.isEmpty()) return new ArrayList<>();

        // Get sample documents to discover fields
        List<Document> samples = cache.values().stream().limit(10).collect(Collectors.toList());

        Set<String> availableFields = new TreeSet<>(); // TreeSet for sorted results

        for (Document doc : samples)
        {
            for (Map.Entry<String, Object> entry : doc.entrySet())
            {
                String field = entry.getKey();
                Object value = entry.getValue();

                // Include string fields and some other types that can be displayed
                if (value != null && !isEmptyValue(value))
                {
                    if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Date)
                    {
                        // Exclude technical fields
                        if (!field.equals("_id") && !field.equals("__v") && !field.startsWith("_") && !field.endsWith("Id"))
                        {
                            availableFields.add(field);
                        }
                    }
                }
            }
        }

        return new ArrayList<>(availableFields);
    }

    /**
     * Cache a collection for later use
     */
    private void cacheCollectionIfNeeded(String collectionName)
    {
        cacheAndAnalyzeCollection(collectionName, new HashSet<>());
    }

    /**
     * Phase 3: Discover expanded fields from relationships
     */
    private void discoverExpandedFields()
    {
        // Use RelationExpander to discover expanded field structure
        RelationExpander expander = new RelationExpander(database);

        // Sample some documents to discover expanded structure
        MongoCollection<Document> collection = database.getCollection(collectionName);
        int sampleCount = Math.min(100, sampleSize / 10);

        logger.info("Sampling {} documents to discover expanded field structure", sampleCount);

        try (MongoCursor<Document> cursor = collection.find().limit(sampleCount).iterator())
        {
            while (cursor.hasNext())
            {
                Document doc = cursor.next();
                Document expanded = expander.expandDocument(doc, collectionName, 1);

                // Reset field tracking for this document to avoid double-counting
                currentDocumentFields = new HashSet<>();
                // Discover fields in expanded document
                discoverFieldsInDocument(expanded, "", collectionName, 0);
            }
        }

        // Calculate compound sparsity for expanded fields
        calculateCompoundSparsityForExpandedFields();

        logger.info("Expanded field discovery complete");
    }

    /**
     * Calculate compound sparsity for expanded fields
     * This samples referenced collections to understand how often fields exist
     */
    private void calculateCompoundSparsityForExpandedFields()
    {
        logger.info("Calculating compound sparsity for expanded fields");

        // Track parent field statistics
        Map<String, ParentFieldStats> parentFieldStats = new HashMap<>();

        // First, collect statistics about parent fields
        for (Map.Entry<String, FieldMetadata> entry : fieldMetadataMap.entrySet())
        {
            String fieldPath = entry.getKey();
            FieldMetadata metadata = entry.getValue();

            // Check if this is an expanded field
            if (fieldPath.contains("_expanded"))
            {
                // Extract parent field name (e.g., "property" from "property_expanded.city")
                String parentField = fieldPath.substring(0, fieldPath.indexOf("_expanded"));

                // Get or create parent stats
                ParentFieldStats parentStats = parentFieldStats.computeIfAbsent(parentField, k ->
                {
                    ParentFieldStats stats = new ParentFieldStats();
                    stats.parentField = k;

                    // Get parent field metadata to understand its presence
                    FieldMetadata parentMeta = fieldMetadataMap.get(k);
                    if (parentMeta != null)
                    {
                        stats.parentOccurrences = parentMeta.totalOccurrences;
                        stats.targetCollection = parentMeta.relationshipTarget;
                    }
                    return stats;
                });

                // Add this expanded field to the parent's list
                parentStats.expandedFields.add(fieldPath);
            }
        }

        // Now sample referenced collections to understand field sparsity
        for (ParentFieldStats parentStats : parentFieldStats.values())
        {
            if (parentStats.targetCollection == null || parentStats.targetCollection.isEmpty())
            {
                // Try to guess the collection
                parentStats.targetCollection = guessCollectionFromFieldName(parentStats.parentField);
            }

            if (parentStats.targetCollection != null)
            {
                sampleReferencedCollection(parentStats);
            }
        }

        // Apply compound sparsity to expanded field metadata
        for (ParentFieldStats parentStats : parentFieldStats.values())
        {
            double parentPresence = (double) parentStats.parentOccurrences / actualDocumentsScanned;

            for (String expandedField : parentStats.expandedFields)
            {
                FieldMetadata expandedMeta = fieldMetadataMap.get(expandedField);
                if (expandedMeta != null)
                {
                    // Extract the nested field path (e.g., "city" from "property_expanded.city")
                    String nestedPath = expandedField.substring(expandedField.indexOf("_expanded.") + "_expanded.".length());

                    // Get field presence in referenced collection
                    Double fieldPresence = parentStats.fieldPresenceMap.get(nestedPath);
                    if (fieldPresence == null)
                    {
                        fieldPresence = 0.0;  // Field not found in sample
                    }

                    // Calculate compound sparsity
                    double compoundSparsity = parentPresence * fieldPresence;

                    // Update the metadata with compound statistics
                    // We'll adjust totalOccurrences to reflect actual expected presence
                    expandedMeta.compoundSparsity = compoundSparsity;
                    expandedMeta.adjustedOccurrences = (int) (actualDocumentsScanned * compoundSparsity);

                    if (compoundSparsity < sparseFieldThreshold)
                    {
                        logger.debug("Expanded field '{}' has compound sparsity {}% (parent: {}%, field: {}%)",
                                expandedField, String.format("%.2f", compoundSparsity * 100),
                                String.format("%.2f", parentPresence * 100),
                                String.format("%.2f", fieldPresence * 100));
                    }
                }
            }
        }
    }

    /**
     * Sample a referenced collection to understand field sparsity
     */
    private void sampleReferencedCollection(ParentFieldStats parentStats)
    {
        try
        {
            MongoCollection<Document> refCollection = database.getCollection(parentStats.targetCollection);

            // Sample a subset of documents
            int sampleSize = 1000;
            long totalDocs = refCollection.estimatedDocumentCount();

            if (totalDocs == 0)
            {
                logger.warn("Referenced collection '{}' is empty", parentStats.targetCollection);
                return;
            }

            logger.debug("Sampling {} documents from {} (total: {})",
                    Math.min(sampleSize, totalDocs), parentStats.targetCollection, totalDocs);

            Map<String, Integer> fieldCounts = new HashMap<>();
            int docsScanned = 0;

            try (MongoCursor<Document> cursor = refCollection.find().limit(sampleSize).iterator())
            {
                while (cursor.hasNext())
                {
                    Document doc = cursor.next();
                    docsScanned++;

                    // Count field presence
                    countFieldsInDocument(doc, "", fieldCounts);
                }
            }

            // Calculate field presence percentages
            for (Map.Entry<String, Integer> entry : fieldCounts.entrySet())
            {
                String fieldPath = entry.getKey();
                int count = entry.getValue();
                double presence = (double) count / docsScanned;
                parentStats.fieldPresenceMap.put(fieldPath, presence);
            }

            logger.debug("Sampled {} documents from {}, found {} unique fields",
                    docsScanned, parentStats.targetCollection, fieldCounts.size());
        } catch (Exception e)
        {
            logger.warn("Failed to sample collection '{}': {}", parentStats.targetCollection, e.getMessage());
        }
    }

    /**
     * Recursively count field presence in a document
     */
    private void countFieldsInDocument(Document doc, String prefix, Map<String, Integer> fieldCounts)
    {
        for (Map.Entry<String, Object> entry : doc.entrySet())
        {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            // Skip technical fields
            if (fieldName.equals("_id") || fieldName.equals("__v"))
            {
                continue;
            }

            String fieldPath = prefix.isEmpty() ? fieldName : prefix + "." + fieldName;

            if (value != null && !isEmptyValue(value))
            {
                fieldCounts.merge(fieldPath, 1, Integer::sum);

                // Recurse into nested documents
                if (value instanceof Document)
                {
                    countFieldsInDocument((Document) value, fieldPath, fieldCounts);
                }
            }
        }
    }

    /**
     * Helper class to track parent field statistics
     */
    private static class ParentFieldStats
    {
        String parentField;
        String targetCollection;
        int parentOccurrences;
        List<String> expandedFields = new ArrayList<>();
        Map<String, Double> fieldPresenceMap = new HashMap<>();  // field path -> presence percentage
    }

    /**
     * Phase 4: Build configuration from metadata
     */
    private void buildConfiguration(DiscoveryConfiguration config)
    {
        Set<String> processedArrays = new HashSet<>();

        for (Map.Entry<String, FieldMetadata> entry : fieldMetadataMap.entrySet())
        {
            FieldMetadata metadata = entry.getValue();
            String fieldPath = metadata.fieldPath;

            // Skip array internals - they'll be handled with their parent array
            if (fieldPath.contains("[]"))
            {
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

            if (!metadata.sampleValues.isEmpty())
            {
                List<String> samples = new ArrayList<>(metadata.sampleValues);
                samples.sort(String::compareTo);
                stats.setSampleValues(samples.subList(0, Math.min(5, samples.size())));
            }

            // Handle arrays specially
            if ("array".equals(metadata.dataType))
            {
                stats.setAvgArrayLength(metadata.getAvgArrayLength());
                stats.setMaxArrayLength(metadata.getMaxArrayLength());

                // Configure array handling based on array internals
                FieldConfiguration.ArrayConfiguration arrayConfig = configureArray(fieldPath, metadata);
                fieldConfig.setArrayConfig(arrayConfig);

                // Track required collection if this array references another collection
                if (arrayConfig.getReferenceCollection() != null)
                {
                    config.addRequiredCollection(arrayConfig.getReferenceCollection());
                }
            }

            fieldConfig.setStatistics(stats);
            config.getFields().add(fieldConfig);

            // Track required collections for direct references
            if (metadata.relationshipTarget != null)
            {
                config.addRequiredCollection(metadata.relationshipTarget);
            }
        }

        // Sort fields by path for consistent ordering
        config.getFields().sort(Comparator.comparing(FieldConfiguration::getFieldPath));
    }

    /**
     * Configure array based on its internal structure
     */
    private FieldConfiguration.ArrayConfiguration configureArray(String arrayPath, FieldMetadata metadata)
    {
        FieldConfiguration.ArrayConfiguration arrayConfig = new FieldConfiguration.ArrayConfiguration();
        arrayConfig.setObjectType(metadata.arrayObjectType);

        ArrayInternals internals = arrayInternalsMap.get(arrayPath);

        if (internals != null)
        {
            if (internals.hasDirectObjectIds && internals.targetCollection != null)
            {
                // Pattern 1: Direct array of ObjectIds
                arrayConfig.setReferenceCollection(internals.targetCollection);

                // Find best display field and available fields from cached collection
                Map<ObjectId, Document> cache = collectionCache.get(internals.targetCollection);
                if (cache != null)
                {
                    String displayField = findBestDisplayField(internals.targetCollection, cache, internals.sampleIds);
                    arrayConfig.setExtractField(displayField);

                    // Set available fields so user knows what options are available
                    List<String> availableFields = getAvailableFields(internals.targetCollection, cache);
                    arrayConfig.setAvailableFields(availableFields);
                }
            } else if (internals.objectIdField != null && internals.targetCollection != null)
            {
                // Pattern 2: Array of objects with ObjectId field
                arrayConfig.setReferenceField(internals.objectIdField);
                arrayConfig.setReferenceCollection(internals.targetCollection);

                // Find best display field and available fields from cached collection
                Map<ObjectId, Document> cache = collectionCache.get(internals.targetCollection);
                if (cache != null)
                {
                    String displayField = findBestDisplayField(internals.targetCollection, cache, internals.sampleIds);
                    arrayConfig.setExtractField(displayField);

                    // Set available fields so user knows what options are available
                    List<String> availableFields = getAvailableFields(internals.targetCollection, cache);
                    arrayConfig.setAvailableFields(availableFields);
                }
            } else if ("object".equals(metadata.arrayObjectType))
            {
                // Array of objects without references - find best field to extract
                String extractField = findBestExtractFieldFromInternals(internals);
                arrayConfig.setExtractField(extractField);

                // For non-reference arrays, available fields are the internal fields
                if (internals.internalFields != null && !internals.internalFields.isEmpty())
                {
                    List<String> availableFields = new ArrayList<>(internals.internalFields);
                    availableFields.sort(String::compareTo);
                    arrayConfig.setAvailableFields(availableFields);
                }
            }
        } else if ("object".equals(metadata.arrayObjectType))
        {
            // Fallback for arrays of objects
            String extractField = findBestExtractField(arrayPath);
            arrayConfig.setExtractField(extractField);
        }

        return arrayConfig;
    }

    /**
     * Find best field to extract from array objects using internals
     */
    private String findBestExtractFieldFromInternals(ArrayInternals internals)
    {
        List<String> preferredNames = Arrays.asList("name", "title", "description", "value", "date", "dateTime");

        for (String preferred : preferredNames)
        {
            if (internals.internalFields.contains(preferred))
            {
                return preferred;
            }
        }

        // Return first non-ID field
        return internals.internalFields.stream().filter(field -> !field.endsWith("Id") && !field.equals("_id")).findFirst().orElse(null);
    }

    /**
     * Find the best field to extract from array objects (fallback method)
     */
    private String findBestExtractField(String arrayFieldPath)
    {
        // This is the old method - kept as fallback
        return null;
    }

    /**
     * Phase 5: Apply filtering rules to determine which fields to include
     */
    private void applyFilteringRules(DiscoveryConfiguration config)
    {
        int excluded = 0;
        int businessIdsKept = 0;
        int sparseFieldsExcluded = 0;

        for (FieldConfiguration field : config.getFields())
        {
            boolean shouldInclude = false;
            String fieldPath = field.getFieldPath();

            // Always include business IDs if they have any data
            if (isBusinessId(fieldPath))
            {
                FieldConfiguration.FieldStatistics stats = field.getStatistics();
                if (stats != null && stats.getDistinctNonNullValues() > 0)
                {
                    shouldInclude = true;
                    businessIdsKept++;
                }
            }
            // Exclude technical IDs
            else if (isTechnicalId(fieldPath))
            {
                shouldInclude = false;
            }
            // Apply filtering rules
            else
            {
                FieldConfiguration.FieldStatistics stats = field.getStatistics();
                if (stats != null)
                {
                    int distinctValues = stats.getDistinctNonNullValues() != null ? stats.getDistinctNonNullValues() : 0;

                    // First check minimum distinct values
                    if (distinctValues < minDistinctNonNullValues)
                    {
                        shouldInclude = false;
                    } else
                    {
                        // Check sparse field threshold based on actual documents scanned
                        Integer totalOccurrences = stats.getTotalOccurrences();

                        // For expanded fields, check if we have compound sparsity calculated
                        if (fieldPath.contains("_expanded"))
                        {
                            FieldMetadata metadata = fieldMetadataMap.get(fieldPath);
                            if (metadata != null && metadata.compoundSparsity != null)
                            {
                                // Use compound sparsity for expanded fields
                                if (metadata.compoundSparsity < sparseFieldThreshold)
                                {
                                    shouldInclude = false;
                                    sparseFieldsExcluded++;
                                    logger.debug("Excluding sparse expanded field '{}': compound sparsity {}% (threshold: {}%)",
                                            fieldPath, String.format("%.2f", metadata.compoundSparsity * 100),
                                            String.format("%.0f", sparseFieldThreshold * 100));
                                } else
                                {
                                    shouldInclude = true;
                                }
                            } else
                            {
                                // Fall back to regular sparsity check
                                shouldInclude = checkRegularSparsity(totalOccurrences);
                            }
                        } else
                        {
                            // Regular fields use normal sparsity check
                            shouldInclude = checkRegularSparsity(totalOccurrences);
                        }
                    }
                }
            }

            field.setInclude(shouldInclude);
            if (!shouldInclude)
            {
                excluded++;
            }
        }

        logger.info("Filtering complete: {} fields excluded ({} sparse), {} business IDs kept",
                excluded, sparseFieldsExcluded, businessIdsKept);
    }

    /**
     * Check regular sparsity for non-expanded fields
     */
    private boolean checkRegularSparsity(Integer totalOccurrences)
    {
        // Use actual documents scanned as the base for percentage calculation
        // This handles fields that don't exist in all documents (sparse fields)
        if (actualDocumentsScanned > 0)
        {
            // For sparse fields, totalOccurrences represents how many documents have the field
            // We want to exclude fields that appear in less than threshold% of all documents
            double fieldPresencePercentage = (double) (totalOccurrences != null ? totalOccurrences : 0) / actualDocumentsScanned;

            return fieldPresencePercentage >= sparseFieldThreshold;
        } else
        {
            return true;  // Include if we don't have document count
        }
    }

    /**
     * Check if a field is a business ID
     */
    private boolean isBusinessId(String fieldPath)
    {
        String lastPart = getLastSegment(fieldPath);
        return BUSINESS_IDS.contains(lastPart);
    }

    /**
     * Check if a field is a technical ID that should be excluded
     */
    private boolean isTechnicalId(String fieldPath)
    {
        String lastPart = getLastSegment(fieldPath);

        if ("_id".equals(lastPart) || "__v".equals(lastPart))
        {
            return true;
        }

        // Exclude fields ending with Id unless they're business IDs
        if (lastPart.endsWith("Id") && !isBusinessId(fieldPath))
        {
            return true;
        }

        return false;
    }

    /**
     * Get the last segment of a field path
     */
    private String getLastSegment(String fieldPath)
    {
        return fieldPath.contains(".") ? fieldPath.substring(fieldPath.lastIndexOf(".") + 1) : fieldPath;
    }

    /**
     * Ensure business IDs are discovered even if not in sample
     */
    private void ensureBusinessIdsDiscovered()
    {
        // Don't add fields artificially - they should be discovered from data
        // If they're not in the sample, they're probably not in this collection
    }

    /**
     * Guess collection name from field name
     */
    private String guessCollectionFromFieldName(String fieldName)
    {
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

        for (Map.Entry<String, String> entry : mappings.entrySet())
        {
            if (normalized.contains(entry.getKey()))
            {
                return entry.getValue();
            }
        }

        // Try pluralizing
        if (!normalized.endsWith("s"))
        {
            return normalized + "s";
        }

        return normalized;
    }

    /**
     * Generate an audit tree file showing the field expansion hierarchy
     */
    private void generateAuditTree(DiscoveryConfiguration config)
    {
        try
        {
            File auditFile = new File("config", collectionName + "_expansion_audit.txt");
            auditFile.getParentFile().mkdirs();

            StringBuilder audit = new StringBuilder();
            audit.append("=================================================================\n");
            audit.append("FIELD EXPANSION AUDIT TREE\n");
            audit.append("=================================================================\n");
            audit.append("Collection: ").append(collectionName).append("\n");
            audit.append("Generated: ").append(new Date()).append("\n");
            audit.append("Total Fields: ").append(config.getFields().size()).append("\n");
            audit.append("Included Fields: ").append(config.getIncludedFields().size()).append("\n");
            audit.append("Expansion Depth: ").append(expansionDepth).append("\n");
            audit.append("=================================================================\n\n");

            // Generate the tree output
            Set<String> processedRoots = new HashSet<>();
            for (FieldConfiguration field : config.getFields())
            {
                String rootPath = field.getFieldPath();

                // Only process root fields (no dots and not expanded)
                if (!rootPath.contains(".") && !rootPath.contains("_expanded"))
                {
                    if (processedRoots.add(rootPath))
                    {
                        appendFieldToAudit(audit, field, config, 0, new HashSet<>());
                    }
                }
            }

            // Add summary section
            audit.append("\n=================================================================\n");
            audit.append("EXPANSION SUMMARY\n");
            audit.append("=================================================================\n");

            // List expanded relationships
            audit.append("\nExpanded Relationships:\n");
            Set<String> expandedRelationships = new HashSet<>();
            for (FieldConfiguration field : config.getFields())
            {
                if (field.getFieldPath().contains("_expanded"))
                {
                    String originalField = field.getFieldPath().split("_expanded")[0];
                    String relationship = originalField + " -> " + field.getSourceCollection();
                    if (expandedRelationships.add(relationship))
                    {
                        audit.append("  - ").append(relationship).append("\n");
                    }
                }
            }

            // List array references
            audit.append("\nArray References:\n");
            for (FieldConfiguration field : config.getFields())
            {
                if ("array".equals(field.getDataType()) && field.getArrayConfig() != null)
                {
                    FieldConfiguration.ArrayConfiguration arrayConfig = field.getArrayConfig();
                    if (arrayConfig.getReferenceCollection() != null)
                    {
                        audit.append("  - ").append(field.getFieldPath()).append(" -> ")
                                .append(arrayConfig.getReferenceCollection());
                        if (arrayConfig.getExtractField() != null)
                        {
                            audit.append(" (extracting: ").append(arrayConfig.getExtractField()).append(")");
                        }
                        audit.append("\n");
                    }
                }
            }

            // List fields that could be expanded but weren't
            audit.append("\nUnexpanded ObjectId Fields (potential relationships):\n");
            for (FieldConfiguration field : config.getFields())
            {
                if ("objectId".equals(field.getDataType()) &&
                        !field.getFieldPath().contains("_expanded") &&
                        !field.getFieldPath().equals("_id"))
                {
                    audit.append("  - ").append(field.getFieldPath());
                    if (field.getRelationshipTarget() != null)
                    {
                        audit.append(" (guessed target: ").append(field.getRelationshipTarget()).append(")");
                    }
                    audit.append("\n");
                }
            }

            // Write to file
            java.nio.file.Files.write(auditFile.toPath(), audit.toString().getBytes());
            logger.info("Audit tree saved to: {}", auditFile.getAbsolutePath());
        } catch (IOException e)
        {
            logger.error("Failed to generate audit tree", e);
        }
    }

    /**
     * Append a field and its children to the audit output
     */
    private void appendFieldToAudit(StringBuilder audit, FieldConfiguration field,
                                    DiscoveryConfiguration config, int depth, Set<String> visited)
    {
        // Prevent infinite recursion
        if (visited.contains(field.getFieldPath()))
        {
            return;
        }
        visited.add(field.getFieldPath());

        // Indentation
        String indent = "  ".repeat(depth);

        // Field name and type
        audit.append(indent).append(field.getFieldPath());
        audit.append(" [").append(field.getDataType()).append("]");

        // Include status
        if (!field.isInclude())
        {
            audit.append(" (EXCLUDED)");
        }

        // Statistics
        if (field.getStatistics() != null)
        {
            Integer distinctValues = field.getStatistics().getDistinctNonNullValues();
            if (distinctValues != null)
            {
                audit.append(" - ").append(distinctValues).append(" distinct values");
            }
        }

        // Array configuration
        if ("array".equals(field.getDataType()) && field.getArrayConfig() != null)
        {
            FieldConfiguration.ArrayConfiguration arrayConfig = field.getArrayConfig();
            if (arrayConfig.getReferenceCollection() != null)
            {
                audit.append(" -> ").append(arrayConfig.getReferenceCollection());
                if (arrayConfig.getExtractField() != null)
                {
                    audit.append(".").append(arrayConfig.getExtractField());
                }
            }
        }

        // ObjectId reference
        if ("objectId".equals(field.getDataType()) && field.getRelationshipTarget() != null)
        {
            audit.append(" -> ").append(field.getRelationshipTarget());
        }

        audit.append("\n");

        // Find child fields (fields that start with this path followed by a dot)
        String pathPrefix = field.getFieldPath() + ".";
        Set<String> processedChildren = new HashSet<>();
        for (FieldConfiguration child : config.getFields())
        {
            if (child.getFieldPath().startsWith(pathPrefix) && !child.getFieldPath().contains("_expanded"))
            {
                // Only direct children (no additional dots after the prefix)
                String remainder = child.getFieldPath().substring(pathPrefix.length());
                if (!remainder.contains("."))
                {
                    if (processedChildren.add(child.getFieldPath()))
                    {
                        appendFieldToAudit(audit, child, config, depth + 1, visited);
                    }
                }
            }
        }

        // Also check for expanded versions
        String expandedPath = field.getFieldPath() + "_expanded";
        for (FieldConfiguration expanded : config.getFields())
        {
            if (expanded.getFieldPath().equals(expandedPath) ||
                    expanded.getFieldPath().startsWith(expandedPath + "."))
            {
                if (expanded.getFieldPath().equals(expandedPath))
                {
                    // This is the expanded root
                    audit.append(indent).append("  └─ EXPANDED:\n");
                }
                appendFieldToAudit(audit, expanded, config, depth + 2, visited);
            }
        }
    }
}