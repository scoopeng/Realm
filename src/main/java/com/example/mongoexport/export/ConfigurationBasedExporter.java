package com.example.mongoexport.export;

import com.example.mongoexport.AbstractUltraExporter;
import com.example.mongoexport.ExportOptions;
import com.example.mongoexport.RelationExpander;
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
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final DiscoveryConfiguration configuration;
    private final List<FieldConfiguration> includedFields;
    private final Map<String, Map<ObjectId, Document>> collectionCache = new HashMap<>();
    private final Set<String> cachedCollectionNames = new HashSet<>();
    private RelationExpander relationExpander;

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

        // Initialize relation expander if we have relationships
        if (!config.getRequiredCollections().isEmpty())
        {
            this.relationExpander = new RelationExpander(database);
        }

        logger.info("Loaded configuration for collection: {}", config.getCollection());
        logger.info("Total fields: {}, Included fields: {}", config.getFields().size(), includedFields.size());
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
        return includedFields.stream().map(field -> configuration.getExportSettings().isUseBusinessNames() ? field.getBusinessName() : field.getFieldPath()).toArray(String[]::new);
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
            // Write header
            writeHeader(csvWriter);

            // Export data
            MongoCollection<Document> collection = database.getCollection(configuration.getCollection());
            long totalDocs = collection.countDocuments();
            logger.info("Exporting {} documents from {}", totalDocs, configuration.getCollection());

            int processedCount = 0;
            int batchSize = configuration.getExportSettings().getBatchSize();
            long startTime = System.currentTimeMillis();

            try (MongoCursor<Document> cursor = collection.find().batchSize(batchSize).iterator())
            {
                List<Document> batch = new ArrayList<>(batchSize);

                while (cursor.hasNext())
                {
                    batch.add(cursor.next());

                    if (batch.size() >= batchSize)
                    {
                        processBatch(batch, csvWriter);
                        processedCount += batch.size();
                        logProgress(processedCount, totalDocs, startTime);
                        batch.clear();
                    }
                }

                // Process remaining documents
                if (!batch.isEmpty())
                {
                    processBatch(batch, csvWriter);
                    processedCount += batch.size();
                }
            }

            logger.info("Export complete: {} documents processed", processedCount);
            return processedCount;
        });
    }

    /**
     * Write CSV header
     */
    private void writeHeader(CSVWriter csvWriter)
    {
        String[] header = includedFields.stream().map(field -> configuration.getExportSettings().isUseBusinessNames() ? field.getBusinessName() : field.getFieldPath()).toArray(String[]::new);

        csvWriter.writeNext(header);
        csvWriter.flushQuietly();
    }

    /**
     * Process a batch of documents
     */
    private void processBatch(List<Document> batch, CSVWriter csvWriter)
    {
        for (Document doc : batch)
        {
            // Expand relationships if needed
            if (relationExpander != null)
            {
                doc = relationExpander.expandDocument(doc, configuration.getCollection(), configuration.getDiscoveryParameters().getExpansionDepth());
            }

            // Extract values for included fields
            String[] row = extractRow(doc);
            csvWriter.writeNext(row);
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

        // Handle ObjectIds
        if (value instanceof ObjectId)
        {
            return value.toString();
        }

        // Handle documents (shouldn't happen with proper field paths)
        if (value instanceof Document)
        {
            return ((Document) value).toJson();
        }

        // Default string conversion
        return value.toString();
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

        logger.info("Progress: {}/{} documents ({:.1f}%), Rate: {:.0f} docs/sec, ETA: {} seconds", processed, total, (processed * 100.0 / total), rate, remaining);
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