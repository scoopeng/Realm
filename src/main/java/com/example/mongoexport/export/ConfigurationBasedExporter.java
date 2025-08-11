package com.example.mongoexport.export;

import com.example.mongoexport.AbstractUltraExporter;
import com.example.mongoexport.ExportOptions;
import com.example.mongoexport.config.DiscoveryConfiguration;
import com.example.mongoexport.config.FieldConfiguration;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.opencsv.CSVWriter;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
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
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd H1H:mm:ss");

    private final DiscoveryConfiguration configuration;
    private final List<FieldConfiguration> includedFields;
    private final Map<String, Map<ObjectId, Document>> collectionCache = new HashMap<>();
    private final Set<String> cachedCollectionNames = new HashSet<>();
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

        // Log if there are any expanded fields (we won't actually expand them)
        long expandedCount = includedFields.stream()
                .filter(f -> f.getFieldPath().contains("_expanded"))
                .count();
        if (expandedCount > 0)
        {
            logger.warn("Found {} expanded fields in configuration - these will be skipped for performance", expandedCount);
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

        logger.info("Cached {} collections with {} total documents", cachedCollectionNames.size(), collectionCache.values().stream().mapToInt(Map::size).sum());
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

        // Check if collection exists
        boolean exists = database.listCollectionNames().into(new ArrayList<>()).contains(collectionName);

        if (!exists)
        {
            logger.warn("Collection {} does not exist, skipping", collectionName);
            return;
        }

        MongoCollection<Document> collection = database.getCollection(collectionName);
        long count = collection.estimatedDocumentCount();

        // Cache collections up to 100K documents
        if (count <= 100000)
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
            logger.info("  Cached {} documents", cache.size());
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
        exportWithStatistics(csvWriter ->
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
                        // Pre-cache required documents for this batch
                        preCacheRequiredDocuments(batch);
                        
                        processBatch(batch, csvWriter);
                        processedCount += batch.size();
                        logProgress(processedCount, docsToExport, startTime);
                        batch.clear();
                    }
                }

                // Process remaining documents
                if (!batch.isEmpty())
                {
                    // Pre-cache required documents for this batch
                    preCacheRequiredDocuments(batch);
                    
                    processBatch(batch, csvWriter);
                    processedCount += batch.size();
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
                if (field.getRelationshipTarget() != null && !field.getRelationshipTarget().isEmpty())
                {
                    Object value = extractFieldValue(doc, field);
                    if (value instanceof ObjectId)
                    {
                        String targetCollection = field.getRelationshipTarget();
                        idsToLoad.computeIfAbsent(targetCollection, k -> new HashSet<>()).add((ObjectId) value);
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
    private void processBatch(List<Document> batch, CSVWriter csvWriter)
    {
        for (Document doc : batch)
        {
            // Extract values for included fields
            String[] row = extractRow(doc);
            csvWriter.writeNext(row);

            // Track statistics if enabled
            if (fieldStatisticsCollector != null)
            {
                fieldStatisticsCollector.recordRow(row, buildComprehensiveHeaders());
            }
        }
        csvWriter.flushQuietly();
    }

    /**
     * Extract a row of values from a document
     */
    private String[] extractRow(Document doc)
    {
        String[] row = new String[includedFields.size()];

        for (int i = 0; i < includedFields.size(); i++)
        {
            FieldConfiguration field = includedFields.get(i);
            Object value = extractFieldValue(doc, field);
            row[i] = formatValue(value, field);
        }

        return row;
    }

    /**
     * Extract value for a field from document
     */
    private Object extractFieldValue(Document doc, FieldConfiguration field)
    {
        // Skip expanded fields for performance - they require expensive lookups
        if (field.getFieldPath().contains("_expanded"))
        {
            return null;
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
                
                // Try to look up the referenced document
                Document referencedDoc = lookupDocument(targetCollection, (ObjectId) value);
                if (referencedDoc != null)
                {
                    // Try to find a good display field
                    String displayValue = extractBestDisplayField(referencedDoc);
                    if (displayValue != null && !displayValue.isEmpty())
                    {
                        return displayValue;
                    }
                }
            }
            
            // Fall back to just the ObjectId string
            return value.toString();
        }

        // Handle documents (shouldn't happen with proper field paths)
        if (value instanceof Document)
        {
            return ((Document) value).toJson();
        }

        // Handle booleans - return as unquoted true/false
        if (value instanceof Boolean)
        {
            return value.toString();
        }

        // Handle numbers - return as unquoted numbers
        if (value instanceof Number)
        {
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
            
            // If cache exists but document not found, try lazy loading
            if (cachedCollectionNames.contains(collectionName))
            {
                // Lazy load this specific document
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
            "fullName",       // For agents
            "name",           // Generic name field
            "title",          // For various entities
            "streetAddress",  // For addresses
            "displayName",    // Display-specific field
            "description"     // Fallback description
        };
        
        for (String fieldName : preferredFields)
        {
            Object value = doc.get(fieldName);
            if (value != null && !value.toString().trim().isEmpty())
            {
                return value.toString();
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

        // Sort if requested
        if ("alphanumeric".equals(arrayConfig.getSortOrder()))
        {
            values.sort(String::compareTo);
        }

        // Return based on display mode
        if ("first".equals(arrayConfig.getDisplayMode()) && !values.isEmpty())
        {
            return values.get(0);
        } else
        {
            return String.join(arrayConfig.getDelimiter(), values);
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
}