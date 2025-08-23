package com.example.mongoexport.export;

import com.example.mongoexport.AbstractUltraExporter;
import com.example.mongoexport.ExportOptions;
import com.example.mongoexport.config.DiscoveryConfiguration;
import com.example.mongoexport.config.FieldConfiguration;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exporter that uses a JSON configuration file to determine which fields to export.
 * This is the second phase of the two-phase export process.
 */
public class ConfigurationBasedExporter extends AbstractUltraExporter
{
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationBasedExporter.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final DiscoveryConfiguration configuration;
    private final List<FieldConfiguration> includedFields;
    private final Map<String, Map<ObjectId, Document>> collectionCache = new HashMap<>();
    private final Map<String, Map<ObjectId, String>> displayValueCache = new HashMap<>();
    private final Set<String> cachedCollectionNames = new HashSet<>();
    private final Set<String> fullyLoadedCollections = new HashSet<>();  // Track collections that are fully cached
    private Integer rowLimit = null;
    private String[] cachedHeaders = null;

    /**
     * Create exporter from configuration file
     */
    public ConfigurationBasedExporter(File configFile) throws IOException
    {
        this(DiscoveryConfiguration.loadFromFile(configFile));
    }

    /**
     * Create exporter from configuration object
     */
    public ConfigurationBasedExporter(DiscoveryConfiguration config)
    {
        super(buildExportOptions(config));
        this.configuration = config;
        this.includedFields = config.getIncludedFields();

        logger.info("Loaded configuration for collection: {}", config.getCollection());
        logger.info("Total fields: {}, Included fields: {}", config.getFields().size(), includedFields.size());

        // Log if there are any expanded fields
        long expandedCount = includedFields.stream()
                .filter(f -> f.getFieldPath().contains("_expanded"))
                .count();
        if (expandedCount > 0)
        {
            logger.info("Found {} expanded fields in configuration - will resolve via cached lookups", expandedCount);
        }
    }

    /**
     * Set a row limit for testing purposes
     */
    public void setRowLimit(Integer limit)
    {
        this.rowLimit = limit;
        logger.info("Row limit set to: {} rows", limit);
    }

    /**
     * Build export options from configuration
     */
    private static ExportOptions buildExportOptions(DiscoveryConfiguration config)
    {
        return ExportOptions.builder().enableFieldStatistics(true).enableRelationExpansion(!config.getRequiredCollections().isEmpty()).expansionDepth(config.getDiscoveryParameters().getExpansionDepth()).build();
    }

    @Override
    protected String getCollectionName()
    {
        return configuration.getCollection();
    }

    @Override
    protected String getExportFilePrefix()
    {
        return configuration.getCollection();
    }

    @Override
    protected String[] buildComprehensiveHeaders()
    {
        if (cachedHeaders == null)
        {
            cachedHeaders = includedFields.stream().map(field -> configuration.getExportSettings().isUseBusinessNames() ? field.getBusinessName() : field.getFieldPath()).toArray(String[]::new);
        }
        return cachedHeaders;
    }

    @Override
    protected void loadCollectionsIntoMemory()
    {
        // Collections are loaded on-demand in cacheRequiredCollections
        logger.info("Collections will be cached as needed during export");
    }

    @Override
    public void export()
    {
        logger.info("=== CONFIGURATION-BASED EXPORT ===");
        logger.info("Collection: {}", configuration.getCollection());
        logger.info("Fields to export: {}", includedFields.size());
        logger.info("Required collections for relationships: {}", configuration.getRequiredCollections());

        // Phase 1: Cache required collections
        if (!configuration.getRequiredCollections().isEmpty())
        {
            logger.info("\n=== Phase 1: Caching Required Collections ===");
            cacheRequiredCollections();
        }

        // Phase 2: Export data
        logger.info("\n=== Phase 2: Exporting Data ===");
        performExport();

        // Phase 3: Generate report
        logger.info("\n=== Phase 3: Generating Report ===");
        generateExportReport();
    }

    /**
     * Cache collections needed for relationship expansion
     */
    private void cacheRequiredCollections()
    {
        for (String collectionName : configuration.getRequiredCollections())
        {
            cacheCollection(collectionName);
        }

        // RelationExpander will use its own caching mechanism
        // Our local cache will be used for lookups during export

        logger.info("Cached {} collections with {} total documents ({} fully loaded)", 
                cachedCollectionNames.size(), 
                collectionCache.values().stream().mapToInt(Map::size).sum(),
                fullyLoadedCollections.size());
    }

    /**
     * Cache a single collection
     */
    private void cacheCollection(String collectionName)
    {
        if (cachedCollectionNames.contains(collectionName))
        {
            return;
        }
        
        // NEVER cache the source collection being exported
        if (collectionName.equals(configuration.getCollection()))
        {
            logger.info("Skipping cache for source collection {}", collectionName);
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

        // Cache collections up to 600K documents (matches discovery phase)
        // This ensures people_meta (552K) is fully cached for performance
        if (count <= 600000)
        {
            logger.info("Caching collection {} ({} documents)", collectionName, count);
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
            cachedCollectionNames.add(collectionName);
            fullyLoadedCollections.add(collectionName);  // Mark as fully loaded
            logger.info("  Cached {} documents (fully loaded)", cache.size());
            
            // Pre-compute display values for people collection to speed up lookups
            if ("people".equals(collectionName))
            {
                logger.info("  Pre-computing display values for people collection...");
                Map<ObjectId, String> peopleDisplayCache = new HashMap<>();
                for (Map.Entry<ObjectId, Document> entry : cache.entrySet())
                {
                    String displayValue = extractBestDisplayField(entry.getValue());
                    if (displayValue != null && !displayValue.isEmpty())
                    {
                        peopleDisplayCache.put(entry.getKey(), displayValue);
                    }
                }
                displayValueCache.put(collectionName, peopleDisplayCache);
                logger.info("  Pre-computed {} display values", peopleDisplayCache.size());
            }
        } else
        {
            logger.info("Collection {} too large ({} docs), using lazy loading", collectionName, count);
            cachedCollectionNames.add(collectionName);
            // Create empty cache for lazy loading
            collectionCache.put(collectionName, new HashMap<>());
        }
    }

    /**
     * Perform the actual export
     */
    private void performExport()
    {
        exportWithStatistics(writer ->
        {
            // Export data
            MongoCollection<Document> collection = database.getCollection(configuration.getCollection());
            long totalDocs = collection.countDocuments();

            // Apply row limit if set
            long docsToExport = totalDocs;
            if (rowLimit != null && rowLimit > 0)
            {
                docsToExport = Math.min(rowLimit, totalDocs);
                logger.info("Exporting {} documents from {} (limited from {} total)",
                        docsToExport, configuration.getCollection(), totalDocs);
            } else
            {
                logger.info("Exporting {} documents from {}", totalDocs, configuration.getCollection());
            }

            int processedCount = 0;
            int batchSize = configuration.getExportSettings().getBatchSize();
            long startTime = System.currentTimeMillis();

            // Apply limit to query if needed
            int queryLimit = rowLimit != null ? rowLimit : 0;

            try (MongoCursor<Document> cursor = queryLimit > 0 ?
                    collection.find().limit(queryLimit).batchSize(batchSize).iterator() :
                    collection.find().batchSize(batchSize).iterator())
            {
                List<Document> batch = new ArrayList<>(batchSize);

                while (cursor.hasNext() && (rowLimit == null || processedCount < rowLimit))
                {
                    batch.add(cursor.next());

                    if (batch.size() >= batchSize)
                    {
                        try {
                            // Pre-cache required documents for this batch
                            preCacheRequiredDocuments(batch);
                            
                            processBatch(batch, writer);
                            processedCount += batch.size();
                            logProgress(processedCount, docsToExport, startTime);
                        } catch (Exception e) {
                            logger.error("Error processing batch starting at document {}: {}", 
                                processedCount + 1, e.getMessage(), e);
                            // Skip this batch and continue
                            logger.warn("Skipping batch of {} documents due to error", batch.size());
                        } finally {
                            // Always clear the batch to avoid infinite loops
                            batch.clear();
                        }
                    }
                }

                // Process remaining documents
                if (!batch.isEmpty())
                {
                    try {
                        // Pre-cache required documents for this batch
                        preCacheRequiredDocuments(batch);
                        
                        processBatch(batch, writer);
                        processedCount += batch.size();
                    } catch (Exception e) {
                        logger.error("Error processing final batch starting at document {}: {}", 
                            processedCount + 1, e.getMessage(), e);
                        logger.warn("Skipping final batch of {} documents due to error", batch.size());
                    } finally {
                        batch.clear();
                    }
                }
            }

            logger.info("Export complete: {} documents processed", processedCount);
            return processedCount;
        });
    }

    /**
     * Pre-cache required documents for a batch
     */
    private void preCacheRequiredDocuments(List<Document> batch)
    {
        // Find all ObjectId fields that reference other collections
        Map<String, Set<ObjectId>> idsToLoad = new HashMap<>();
        
        for (Document doc : batch)
        {
            for (FieldConfiguration field : includedFields)
            {
                // Handle expanded fields 
                if (field.getFieldPath().contains("_expanded"))
                {
                    String fieldPath = field.getFieldPath();
                    String baseField = fieldPath.substring(0, fieldPath.indexOf("_expanded"));
                    
                    // Get the base ObjectId
                    Object baseValue = doc.get(baseField);
                    if (baseValue instanceof ObjectId)
                    {
                        String targetCollection = guessTargetCollection(baseField);
                        if (targetCollection != null)
                        {
                            idsToLoad.computeIfAbsent(targetCollection, k -> new HashSet<>()).add((ObjectId) baseValue);
                        }
                    }
                }
                // Also handle regular relationship fields
                else if (field.getRelationshipTarget() != null && !field.getRelationshipTarget().isEmpty())
                {
                    try {
                        Object value = extractFieldValue(doc, field, new HashMap<>());
                        if (value instanceof ObjectId)
                        {
                            String targetCollection = field.getRelationshipTarget();
                            idsToLoad.computeIfAbsent(targetCollection, k -> new HashSet<>()).add((ObjectId) value);
                        }
                    } catch (Exception e) {
                        // Log but don't fail - field extraction during pre-caching is optional
                        logger.debug("Could not extract field {} for pre-caching: {}", field.getFieldPath(), e.getMessage());
                    }
                }
            }
        }
        
        // Batch load documents for each collection
        for (Map.Entry<String, Set<ObjectId>> entry : idsToLoad.entrySet())
        {
            String collectionName = entry.getKey();
            Set<ObjectId> ids = entry.getValue();
            
            // Skip if collection doesn't exist or if we have all documents already cached
            if (!cachedCollectionNames.contains(collectionName))
            {
                continue;
            }
            
            Map<ObjectId, Document> cache = collectionCache.get(collectionName);
            if (cache == null)
            {
                continue;
            }
            
            // Find IDs that aren't already cached
            Set<ObjectId> missingIds = new HashSet<>();
            for (ObjectId id : ids)
            {
                if (!cache.containsKey(id))
                {
                    missingIds.add(id);
                }
            }
            
            // Batch load missing documents
            if (!missingIds.isEmpty())
            {
                MongoCollection<Document> collection = database.getCollection(collectionName);
                Document query = new Document("_id", new Document("$in", new ArrayList<>(missingIds)));
                
                try (MongoCursor<Document> cursor = collection.find(query).iterator())
                {
                    int loaded = 0;
                    while (cursor.hasNext())
                    {
                        Document doc = cursor.next();
                        Object id = doc.get("_id");
                        if (id instanceof ObjectId)
                        {
                            cache.put((ObjectId) id, doc);
                            loaded++;
                        }
                    }
                    
                    if (loaded > 0)
                    {
                        logger.debug("Batch loaded {} documents from {} collection", loaded, collectionName);
                    }
                }
            }
        }
    }
    
    /**
     * Process a batch of documents
     */
    private void processBatch(List<Document> batch, PrintWriter writer)
    {
        int docIndex = 0;
        for (Document doc : batch)
        {
            try {
                // Extract values for included fields
                String[] row = extractRow(doc);
                writeCSVRow(writer, row);

                // Track statistics if enabled
                if (fieldStatisticsCollector != null)
                {
                    fieldStatisticsCollector.recordRow(row, buildComprehensiveHeaders());
                }
                docIndex++;
            } catch (Exception e) {
                ObjectId docId = doc.getObjectId("_id");
                logger.error("Error processing document {} in batch (index {}): {}", 
                    docId != null ? docId.toHexString() : "unknown", docIndex, e.getMessage());
                // Continue processing other documents in the batch
            }
        }
        writer.flush();
    }

    /**
     * Extract a row of values from a document
     */
    private String[] extractRow(Document doc)
    {
        String[] row = new String[includedFields.size()];
        
        // Cache for expanded documents in this row to avoid multiple lookups
        Map<String, Document> rowExpandedCache = new HashMap<>();

        for (int i = 0; i < includedFields.size(); i++)
        {
            FieldConfiguration field = includedFields.get(i);
            Object value = extractFieldValue(doc, field, rowExpandedCache);
            row[i] = formatValue(value, field);
        }

        return row;
    }

    /**
     * Extract value for a field from document
     */
    private Object extractFieldValue(Document doc, FieldConfiguration field, Map<String, Document> rowExpandedCache)
    {
        // Handle special extraction modes for primary and count fields
        if (field.getExtractionMode() != null)
        {
            String mode = field.getExtractionMode();
            String sourceField = field.getSourceField();
            
            if ("count".equals(mode) && sourceField != null)
            {
                // Extract count of array elements
                Object arrayValue = extractNestedField(doc, sourceField);
                if (arrayValue instanceof List)
                {
                    return ((List<?>) arrayValue).size();
                }
                return 0;
            }
            else if ("primary".equals(mode) && sourceField != null)
            {
                // Extract from first element of array
                Object arrayValue = extractNestedField(doc, sourceField);
                if (arrayValue instanceof List && !((List<?>) arrayValue).isEmpty())
                {
                    List<?> list = (List<?>) arrayValue;
                    Object firstElement = list.get(0);
                    
                    // Extract the specific field from the primary element
                    String fieldToExtract = field.getFieldPath().substring(field.getFieldPath().indexOf("[primary].") + 10);
                    
                    // If it's an ObjectId array, we need to look up the document
                    if (firstElement instanceof ObjectId)
                    {
                        // Find the source field configuration to get the reference collection
                        FieldConfiguration sourceFieldConfig = configuration.getFields().stream()
                            .filter(f -> f.getFieldPath().equals(sourceField))
                            .findFirst()
                            .orElse(null);
                        
                        if (sourceFieldConfig != null && sourceFieldConfig.getArrayConfig() != null)
                        {
                            String targetCollection = sourceFieldConfig.getArrayConfig().getReferenceCollection();
                            if (targetCollection != null)
                            {
                                Document referencedDoc = lookupDocument(targetCollection, (ObjectId) firstElement);
                                if (referencedDoc != null)
                                {
                                    return extractNestedField(referencedDoc, fieldToExtract);
                                }
                            }
                        }
                    }
                    // If it's an embedded document array
                    else if (firstElement instanceof Document)
                    {
                        return extractNestedField((Document) firstElement, fieldToExtract);
                    }
                }
                return null;
            }
        }
        
        // Handle expanded fields by looking up the base ObjectId and extracting nested fields
        if (field.getFieldPath().contains("_expanded"))
        {
            // Example: client_expanded.city -> need to get client ObjectId, lookup, then get city
            String fieldPath = field.getFieldPath();
            String baseField = fieldPath.substring(0, fieldPath.indexOf("_expanded"));
            String nestedPath = fieldPath.substring(fieldPath.indexOf("_expanded") + "_expanded".length());
            
            // Remove leading dot if present
            if (nestedPath.startsWith("."))
            {
                nestedPath = nestedPath.substring(1);
            }
            
            // Check if we already have this expanded document cached for this row
            Document referencedDoc = rowExpandedCache.get(baseField);
            
            if (referencedDoc == null)
            {
                // Get the base ObjectId
                Object baseValue = doc.get(baseField);
                if (!(baseValue instanceof ObjectId))
                {
                    return null;
                }
                
                // Determine target collection from the base field
                String targetCollection = guessTargetCollection(baseField);
                if (targetCollection == null)
                {
                    return null;
                }
                
                // Look up the referenced document
                referencedDoc = lookupDocument(targetCollection, (ObjectId) baseValue);
                if (referencedDoc == null)
                {
                    return null;
                }
                
                // Cache it for other expanded fields in this row
                rowExpandedCache.put(baseField, referencedDoc);
            }
            
            // If no nested path, return the whole document (shouldn't happen in practice)
            if (nestedPath.isEmpty())
            {
                return referencedDoc;
            }
            
            // Extract the nested field from the referenced document
            return extractNestedField(referencedDoc, nestedPath);
        }

        String[] pathParts = field.getFieldPath().split("\\.");
        Object current = doc;

        for (String part : pathParts)
        {
            if (current == null)
            {
                return null;
            }

            if (part.endsWith("[]"))
            {
                // Handle array notation
                String fieldName = part.substring(0, part.length() - 2);
                if (current instanceof Document)
                {
                    current = ((Document) current).get(fieldName);
                }
                // Array value will be processed in formatValue
            } else if (current instanceof Document)
            {
                current = ((Document) current).get(part);
            } else if (current instanceof List)
            {
                // Handle array access
                List<?> list = (List<?>) current;
                if (!list.isEmpty() && list.get(0) instanceof Document)
                {
                    // Extract field from array of documents
                    List<Object> values = new ArrayList<>();
                    for (Object item : list)
                    {
                        if (item instanceof Document)
                        {
                            Object fieldValue = ((Document) item).get(part);
                            if (fieldValue != null)
                            {
                                values.add(fieldValue);
                            }
                        }
                    }
                    current = values;
                }
            } else
            {
                return null;
            }
        }

        return current;
    }

    /**
     * Format a value for CSV output
     */
    private String formatValue(Object value, FieldConfiguration field)
    {
        if (value == null)
        {
            return "";
        }
        
        // Check if the value is the string "null" and treat it as empty
        if (value instanceof String && "null".equals(value))
        {
            return "";
        }

        // Handle arrays
        if (value instanceof List)
        {
            return formatArrayValue((List<?>) value, field);
        }

        // Handle dates
        if (value instanceof Date)
        {
            return DATE_FORMAT.format((Date) value);
        }

        // Handle ObjectIds - check if this field references another collection
        if (value instanceof ObjectId)
        {
            // Check if this is a reference to another collection that we should expand
            if (field.getRelationshipTarget() != null && !field.getRelationshipTarget().isEmpty())
            {
                String targetCollection = field.getRelationshipTarget();
                ObjectId objectId = (ObjectId) value;
                
                // Check if we have a cached display value
                Map<ObjectId, String> collectionDisplayCache = displayValueCache.get(targetCollection);
                if (collectionDisplayCache != null)
                {
                    String cachedDisplay = collectionDisplayCache.get(objectId);
                    if (cachedDisplay != null)
                    {
                        return cachedDisplay;
                    }
                }
                
                // Try to look up the referenced document
                Document referencedDoc = lookupDocument(targetCollection, objectId);
                if (referencedDoc != null)
                {
                    // Try to find a good display field
                    String displayValue = extractBestDisplayField(referencedDoc);
                    if (displayValue != null && !displayValue.isEmpty())
                    {
                        // Cache the display value
                        if (collectionDisplayCache == null)
                        {
                            collectionDisplayCache = new HashMap<>();
                            displayValueCache.put(targetCollection, collectionDisplayCache);
                        }
                        collectionDisplayCache.put(objectId, displayValue);
                        return displayValue;
                    }
                }
            }
            
            // Fall back to just the ObjectId string
            return value.toString();
        }

        // Handle documents (shouldn't happen with proper field paths, but description field is a Document)
        if (value instanceof Document)
        {
            Document doc = (Document) value;
            // For description field with "en" key, extract just the text
            if (doc.containsKey("en")) {
                Object enValue = doc.get("en");
                if (enValue != null) {
                    return enValue.toString();
                }
            }
            
            // Try to extract the best display field for person documents
            String displayValue = extractBestDisplayField(doc);
            if (displayValue != null && !displayValue.isEmpty()) {
                return displayValue;
            }
            
            // For other documents, just return first string value found
            for (Object val : doc.values()) {
                if (val != null && !(val instanceof Document) && !(val instanceof List)) {
                    return val.toString();
                }
            }
            return "";
        }

        // Handle booleans - return as unquoted true/false
        if (value instanceof Boolean)
        {
            return value.toString();
        }

        // Handle numbers - return as unquoted numbers
        if (value instanceof Number)
        {
            // Format integers without scientific notation
            if (value instanceof Integer || value instanceof Long)
            {
                return String.valueOf(value);
            }
            // Format floating point numbers, avoiding scientific notation for large values
            else if (value instanceof Double || value instanceof Float)
            {
                double d = ((Number) value).doubleValue();
                // Check if it's actually an integer value stored as double
                if (d == Math.floor(d) && !Double.isInfinite(d))
                {
                    return String.format("%.0f", d);
                }
                else
                {
                    return String.format("%.2f", d);
                }
            }
            return value.toString();
        }

        // Default string conversion
        return value.toString();
    }

    /**
     * Look up a document from cache or database
     */
    private Document lookupDocument(String collectionName, ObjectId id)
    {
        // First check if we have this collection cached
        Map<ObjectId, Document> cache = collectionCache.get(collectionName);
        
        if (cache != null)
        {
            // Check if document is in cache
            Document cached = cache.get(id);
            if (cached != null)
            {
                return cached;
            }
            
            // If cache exists but document not found
            if (cachedCollectionNames.contains(collectionName))
            {
                // Skip database lookup if collection is fully cached
                if (fullyLoadedCollections.contains(collectionName))
                {
                    // Document doesn't exist in the collection
                    return null;
                }
                
                // Only lazy load for partially cached collections
                MongoCollection<Document> collection = database.getCollection(collectionName);
                Document doc = collection.find(new Document("_id", id)).first();
                
                if (doc != null)
                {
                    // Add to cache for future use
                    cache.put(id, doc);
                    return doc;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extract the best display field from a document
     */
    private String extractBestDisplayField(Document doc)
    {
        // Priority order for display fields
        String[] preferredFields = {
            "fullAddress",    // For properties
            "fullName",       // For agents/people
            "name",           // Generic name field
            "title",          // For various entities
            "streetAddress",  // For addresses
            "displayName",    // Display-specific field
            "description"     // Fallback description
        };
        
        for (String fieldName : preferredFields)
        {
            Object value = doc.get(fieldName);
            if (value != null)
            {
                // Special handling for nested Document fields (like name.fullName)
                if (value instanceof Document && fieldName.equals("name"))
                {
                    Document nameDoc = (Document) value;
                    // Try to extract fullName from the nested document
                    Object fullNameValue = nameDoc.get("fullName");
                    if (fullNameValue != null && !fullNameValue.toString().trim().isEmpty())
                    {
                        return fullNameValue.toString();
                    }
                    // Skip this Document field and continue to next preferred field
                    continue;
                }
                
                // Skip Document values (we want string values)
                if (value instanceof Document)
                {
                    continue;
                }
                
                String stringValue = value.toString();
                if (!stringValue.trim().isEmpty())
                {
                    return stringValue;
                }
            }
        }
        
        // If no preferred field found, return null to fall back to ID
        return null;
    }

    /**
     * Format array values based on configuration
     */
    private String formatArrayValue(List<?> list, FieldConfiguration field)
    {
        if (list.isEmpty())
        {
            return "";
        }

        FieldConfiguration.ArrayConfiguration arrayConfig = field.getArrayConfig();
        if (arrayConfig == null)
        {
            // Default handling
            return list.stream().filter(Objects::nonNull).map(Object::toString).sorted().collect(Collectors.joining(", "));
        }

        List<String> values = new ArrayList<>();

        // Check if this array references another collection
        if (arrayConfig.getReferenceCollection() != null)
        {
            Map<ObjectId, Document> cache = collectionCache.get(arrayConfig.getReferenceCollection());

            if (cache != null)
            {
                // We have the collection cached - do lookups
                for (Object item : list)
                {
                    ObjectId idToLookup = null;

                    if (item instanceof ObjectId)
                    {
                        // Pattern 1: Direct array of ObjectIds
                        idToLookup = (ObjectId) item;
                    } else if (item instanceof Document && arrayConfig.getReferenceField() != null)
                    {
                        // Pattern 2: Array of objects with ObjectId field
                        Object idField = ((Document) item).get(arrayConfig.getReferenceField());
                        if (idField instanceof ObjectId)
                        {
                            idToLookup = (ObjectId) idField;
                        }
                    }

                    // Lookup the document and extract the display field
                    if (idToLookup != null)
                    {
                        Document referencedDoc = cache.get(idToLookup);
                        if (referencedDoc != null && arrayConfig.getExtractField() != null)
                        {
                            Object displayValue = referencedDoc.get(arrayConfig.getExtractField());
                            if (displayValue != null)
                            {
                                values.add(displayValue.toString());
                            }
                        }
                    }
                }
            } else
            {
                // Collection not cached - fallback to IDs or object fields
                for (Object item : list)
                {
                    if (item instanceof ObjectId)
                    {
                        values.add(item.toString());
                    } else if (item instanceof Document)
                    {
                        if (arrayConfig.getReferenceField() != null)
                        {
                            Object idField = ((Document) item).get(arrayConfig.getReferenceField());
                            if (idField != null)
                            {
                                values.add(idField.toString());
                            }
                        } else if (arrayConfig.getExtractField() != null)
                        {
                            Object fieldValue = ((Document) item).get(arrayConfig.getExtractField());
                            if (fieldValue != null)
                            {
                                values.add(fieldValue.toString());
                            }
                        }
                    }
                }
            }
        } else
        {
            // No reference collection - extract fields directly
            for (Object item : list)
            {
                if (item instanceof Document && arrayConfig.getExtractField() != null)
                {
                    // Extract specific field from document
                    Object fieldValue = ((Document) item).get(arrayConfig.getExtractField());
                    if (fieldValue != null)
                    {
                        values.add(fieldValue.toString());
                    }
                } else if (item != null)
                {
                    values.add(item.toString());
                }
            }
        }

        // Remove duplicates while preserving order
        List<String> uniqueValues = new ArrayList<>(new LinkedHashSet<>(values));
        
        // Sort if requested
        if ("alphanumeric".equals(arrayConfig.getSortOrder()))
        {
            uniqueValues.sort(String::compareTo);
        }

        // Return based on display mode
        if ("first".equals(arrayConfig.getDisplayMode()) && !uniqueValues.isEmpty())
        {
            return uniqueValues.get(0);
        } else
        {
            return String.join(arrayConfig.getDelimiter(), uniqueValues);
        }
    }

    /**
     * Log export progress
     */
    private void logProgress(int processed, long total, long startTime)
    {
        long elapsed = System.currentTimeMillis() - startTime;
        double rate = processed * 1000.0 / elapsed;
        long remaining = (long) ((total - processed) / rate);

        logger.info("Progress: {}/{} documents ({}%), Rate: {} docs/sec, ETA: {} seconds",
                processed, total,
                String.format("%.1f", processed * 100.0 / total),
                String.format("%.0f", rate),
                remaining);
    }

    /**
     * Generate export report
     */
    private void generateExportReport()
    {
        Map<String, Object> report = new HashMap<>();
        report.put("collection", configuration.getCollection());
        report.put("configFile", DiscoveryConfiguration.getConfigFile(configuration.getCollection()).getPath());
        report.put("fieldsExported", includedFields.size());
        report.put("totalFieldsAvailable", configuration.getFields().size());
        report.put("cachedCollections", cachedCollectionNames);
        report.put("exportTimestamp", new Date());

        // Log report
        logger.info("Export Report:");
        report.forEach((key, value) -> logger.info("  {}: {}", key, value));
    }
    
    /**
     * Extract a nested field from a document using dot notation
     */
    private Object extractNestedField(Document doc, String path)
    {
        String[] parts = path.split("\\.");
        Object current = doc;
        
        for (String part : parts)
        {
            if (current == null)
            {
                return null;
            }
            
            if (current instanceof Document)
            {
                current = ((Document) current).get(part);
            }
            else if (current instanceof List && !part.matches("\\d+"))
            {
                // Handle extracting from array of documents
                List<?> list = (List<?>) current;
                List<Object> values = new ArrayList<>();
                for (Object item : list)
                {
                    if (item instanceof Document)
                    {
                        Object value = ((Document) item).get(part);
                        if (value != null)
                        {
                            values.add(value);
                        }
                    }
                }
                current = values.isEmpty() ? null : values;
            }
            else
            {
                return null;
            }
        }
        
        return current;
    }
    
    /**
     * Guess the target collection name from a field name
     */
    private String guessTargetCollection(String fieldName)
    {
        // Use the same logic as RelationExpander
        if (fieldName.equals("property"))
        {
            return "properties";
        }
        else if (fieldName.equals("client"))
        {
            return "people";
        }
        else if (fieldName.equals("listingBrokerage"))
        {
            return "brokerages";
        }
        else if (fieldName.equals("listingAgent") || fieldName.equals("listingAgentId"))
        {
            return "agents";
        }
        else if (fieldName.equals("buyerAgent") || fieldName.equals("buyerAgentId"))
        {
            return "agents";
        }
        else if (fieldName.equals("buyerBrokerage"))
        {
            return "brokerages";
        }
        // Add more mappings as needed
        
        return null;
    }
}