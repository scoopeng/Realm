package com.example.mongoexport;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.opencsv.CSVWriter;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Fully automatic, generic MongoDB denormalizer that discovers ALL fields dynamically.
 * No hardcoded schemas - completely data-driven and future-proof.
 */
public class AutoDiscoveryExporter extends AbstractUltraExporter {
    private static final Logger logger = LoggerFactory.getLogger(AutoDiscoveryExporter.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // Track all discovered field paths
    private final Set<String> discoveredFieldPaths = new TreeSet<>(); // TreeSet for sorted columns
    private final Map<String, Integer> fieldPathCounts = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> fieldPathValues = new ConcurrentHashMap<>();
    private final Map<String, Integer> fieldPathNullCounts = new ConcurrentHashMap<>(); // Track null occurrences
    private static final int MAX_SAMPLE_VALUES = 100; // Limit samples to prevent memory bloat
    
    // Cache for expanded documents with ID-to-collection tracking
    private final Map<String, Map<ObjectId, Document>> collectionCache = new HashMap<>();
    private final Map<ObjectId, String> idToCollectionMap = new HashMap<>(); // Track which collection each ID belongs to
    
    // Configuration
    private final String collectionName;
    private final int maxExpansionDepth;
    private final boolean autoExpandRelations;
    private final int discoveryBatchSize = 10000; // FIXED: Increased to discover rare fields (was 5000)
    private final int minDistinctNonNullValues = 2; // Minimum distinct non-null values for inclusion
    private final boolean useBusinessNames = true; // Use readable business names
    private final Set<String> includedFieldPaths = new LinkedHashSet<>(); // Fields that meet criteria
    
    // Track discovered foreign key fields and their target collections
    private final Map<String, String> discoveredRelationships = new ConcurrentHashMap<>();
    
    // Dynamic collection caching
    private final Set<String> cachedCollectionNames = new HashSet<>();
    private final Object cacheLock = new Object(); // Thread safety for caching
    
    public AutoDiscoveryExporter(String collectionName, ExportOptions options) {
        super(options != null ? options : ExportOptions.builder()
            .enableFieldStatistics(true)
            .enableRelationExpansion(true)
            .expansionDepth(3)
            .build());
        
        this.collectionName = collectionName;
        this.maxExpansionDepth = 3;  // Expand up to 3 levels deep (restored from 2)
        this.autoExpandRelations = true;  // Enable automatic relation expansion
        this.enableFieldStatistics = true; // Enable field statistics collection
        
        // CRITICAL FIX: The parent class (AbstractUltraExporter) already initialized relationExpander
        // in its constructor when enableRelationExpansion is true. We inherit and use it.
    }
    
    @Override
    protected String getCollectionName() {
        return collectionName;
    }
    
    @Override
    protected String getExportFilePrefix() {
        return collectionName;
    }
    
    @Override
    public void export() {
        logger.info("=== AUTOMATIC DISCOVERY EXPORT ===");
        logger.info("Collection: {}", collectionName);
        logger.info("Settings:");
        logger.info("  - Auto-discover fields: YES");
        logger.info("  - Min distinct non-null values for inclusion: {}", minDistinctNonNullValues);
        logger.info("  - Use business names: {}", useBusinessNames);
        logger.info("  - Expand relationships: {}", autoExpandRelations);
        logger.info("This will automatically discover fields and export only those with meaningful data");
        
        // Phase 1: Discovery (with incremental caching)
        logger.info("\n=== PHASE 1: FIELD DISCOVERY ===");
        discoverAllFields();
        
        // Phase 2: Relationship Discovery (uses cached collections)
        logger.info("\n=== PHASE 2: RELATIONSHIP DISCOVERY ===");
        discoverRelationships();
        
        // Phase 2.5: Filter fields based on distinct values
        logger.info("\n=== PHASE 2.5: FIELD FILTERING ===");
        filterFieldsByDistinctValues();
        
        // Log cache status
        logger.info("\n=== CACHE STATUS ===");
        logger.info("Collections cached during discovery: {}", cachedCollectionNames);
        logger.info("Total cached documents: {}", 
            collectionCache.values().stream().mapToInt(Map::size).sum());
        
        // Phase 3: Export with filtered fields (uses cached collections)
        logger.info("\n=== PHASE 3: EXPORT WITH FILTERED FIELDS ===");
        exportWithAllFields();
        
        // Phase 4: Generate summary
        logger.info("\n=== PHASE 4: SUMMARY GENERATION ===");
        generateFieldReport();
    }
    
    /**
     * Phase 1: Discover all fields by scanning documents - FIXED to use RelationExpander
     */
    private void discoverAllFields() {
        logger.info("Discovering fields in {} collection...", collectionName);
        
        MongoCollection<Document> collection = database.getCollection(collectionName);
        long totalDocs = collection.estimatedDocumentCount();
        logger.info("Scanning sample of {} documents (total: {})", 
            Math.min(discoveryBatchSize, totalDocs), totalDocs);
        
        int scanned = 0;
        try (MongoCursor<Document> cursor = collection.find().limit(discoveryBatchSize).iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                
                // CRITICAL FIX: Use RelationExpander for proper discovery
                if (relationExpander != null && autoExpandRelations && scanned < 1000) {
                    // Expand a subset using RelationExpander to discover expanded fields
                    try {
                        Document expanded = relationExpander.expandDocument(doc, collectionName, maxExpansionDepth);
                        discoverFieldsInDocument(expanded, "", 0);
                    } catch (Exception e) {
                        // If expansion fails, at least discover base fields
                        discoverFieldsInDocument(doc, "", 0);
                        logger.debug("Expansion failed during discovery: {}", e.getMessage());
                    }
                } else {
                    // For remaining documents, just discover base fields
                    discoverFieldsInDocument(doc, "", 0);
                }
                
                scanned++;
                if (scanned % 100 == 0) {
                    logger.info("  Scanned {} documents, found {} unique field paths", 
                        scanned, discoveredFieldPaths.size());
                }
            }
        }
        
        logger.info("Discovery complete: Found {} unique field paths in {} documents", 
            discoveredFieldPaths.size(), scanned);
    }
    
    /**
     * Recursively discover all field paths in a document
     */
    private void discoverFieldsInDocument(Object value, String prefix, int depth) {
        if (depth > maxExpansionDepth) return;
        
        if (value instanceof Document) {
            Document doc = (Document) value;
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                String fieldName = entry.getKey();
                String fieldPath = prefix.isEmpty() ? fieldName : prefix + "." + fieldName;
                Object fieldValue = entry.getValue();
                
                // Track the field path
                discoveredFieldPaths.add(fieldPath);
                fieldPathCounts.merge(fieldPath, 1, Integer::sum);
                
                // Track sample values and nulls - FIXED to prevent memory bloat
                if (fieldValue == null || 
                    (fieldValue instanceof String && ((String) fieldValue).trim().isEmpty()) ||
                    "null".equalsIgnoreCase(String.valueOf(fieldValue))) {
                    fieldPathNullCounts.merge(fieldPath, 1, Integer::sum);
                } else if (!(fieldValue instanceof Document) && !(fieldValue instanceof List)) {
                    Set<String> samples = fieldPathValues.computeIfAbsent(fieldPath, k -> new HashSet<>());
                    if (samples.size() < MAX_SAMPLE_VALUES) {
                        String strValue = fieldValue.toString();
                        // Only store if reasonable size to prevent memory issues
                        if (strValue.length() <= 200) {
                            samples.add(strValue);
                        }
                    }
                }
                
                // Recurse into nested documents
                if (fieldValue instanceof Document) {
                    discoverFieldsInDocument(fieldValue, fieldPath, depth + 1);
                } else if (fieldValue instanceof List) {
                    List<?> list = (List<?>) fieldValue;
                    if (!list.isEmpty()) {
                        Object firstItem = list.get(0);
                        
                        // For arrays, track both the array field and its contents
                        discoveredFieldPaths.add(fieldPath + "[]");
                        
                        if (firstItem instanceof Document) {
                            // Array of documents - discover their structure
                            discoverFieldsInDocument(firstItem, fieldPath + "[]", depth + 1);
                        } else if (firstItem instanceof ObjectId) {
                            // Array of ObjectIds - likely a foreign key relationship
                            discoveredFieldPaths.add(fieldPath + "[].@reference");
                            // Array of ObjectIds - potential reference
                        }
                    }
                } else if (fieldValue instanceof ObjectId) {
                    // Single ObjectId - likely a foreign key
                    discoveredFieldPaths.add(fieldPath + ".@reference");
                    // Single ObjectId - potential reference
                }
            }
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (!list.isEmpty() && list.get(0) instanceof Document) {
                discoverFieldsInDocument(list.get(0), prefix, depth);
            }
        }
    }
    
    /**
     * Phase 2: Discover and expand relationships
     */
    private void discoverRelationships() {
        if (!autoExpandRelations) {
            logger.info("Relation expansion disabled");
            return;
        }
        
        logger.info("Discovering relationships from ObjectId references...");
        
        Set<String> referenceFields = discoveredFieldPaths.stream()
            .filter(path -> path.contains("@reference"))
            .map(path -> path.replace(".@reference", "").replace("[].@reference", ""))
            .collect(Collectors.toSet());
        
        logger.info("Found {} potential foreign key fields", referenceFields.size());
        
        for (String refField : referenceFields) {
            discoverRelationForField(refField);
        }
        
        logger.info("Relationship discovery complete. Total field paths: {}", 
            discoveredFieldPaths.size());
    }
    
    /**
     * Discover what collection a foreign key points to
     */
    private void discoverRelationForField(String fieldPath) {
        // Discovering relation for field
        
        // Common patterns for field names to collection mappings - ENHANCED
        Map<String, String> commonMappings = new HashMap<>();
        commonMappings.put("property", "properties");
        commonMappings.put("listing", "listings");
        commonMappings.put("agent", "agents");
        commonMappings.put("currentagent", "currentAgents"); // handle case variations
        commonMappings.put("listingagent", "agents");
        commonMappings.put("buyeragent", "agents");
        commonMappings.put("selleragent", "agents");
        commonMappings.put("person", "people");
        commonMappings.put("buyer", "people");
        commonMappings.put("seller", "people");
        commonMappings.put("brokerage", "brokerages");
        commonMappings.put("listingbrokerage", "brokerages");
        commonMappings.put("buyerbrokerage", "brokerages");
        commonMappings.put("sellerbrokerage", "brokerages");
        commonMappings.put("team", "teams");
        commonMappings.put("transaction", "transactions");
        commonMappings.put("award", "awards");
        
        // Extract the field name from the path
        String fieldName = fieldPath.contains(".") ? 
            fieldPath.substring(fieldPath.lastIndexOf(".") + 1) : fieldPath;
        fieldName = fieldName.replace("Id", "").replace("[]", "");
        
        // Try to find the target collection
        String targetCollection = null;
        
        // Check common mappings
        for (Map.Entry<String, String> mapping : commonMappings.entrySet()) {
            if (fieldName.toLowerCase().contains(mapping.getKey().toLowerCase())) {
                targetCollection = mapping.getValue();
                break;
            }
        }
        
        // If we found a target collection, cache it IMMEDIATELY
        if (targetCollection != null) {
            logger.info("  {} -> {} (caching and expanding)", fieldPath, targetCollection);
            
            // Track the discovered relationship
            discoveredRelationships.put(fieldPath, targetCollection);
            
            // Cache the collection if not already cached
            cacheCollectionIfNeeded(targetCollection);
            
            // Now expand fields (will be fast since collection is cached)
            expandRelationFields(fieldPath, targetCollection);
        } else {
            logger.debug("  {} -> unknown collection (skipping)", fieldPath);
        }
    }
    
    /**
     * Cache a collection immediately when discovered
     */
    private void cacheCollectionIfNeeded(String collectionName) {
        synchronized (cacheLock) {
            if (cachedCollectionNames.contains(collectionName)) {
                return; // Already cached
            }
            
            try {
                MongoCollection<Document> collection = database.getCollection(collectionName);
                long count = collection.estimatedDocumentCount();
                
                // Cache entire collection
                logger.info("Caching entire {} collection (~{} documents)...", collectionName, count);
                long startTime = System.currentTimeMillis();
                
                Map<ObjectId, Document> cache = new HashMap<>();
                collection.find().forEach(doc -> {
                    ObjectId id = doc.getObjectId("_id");
                    if (id != null) {
                        cache.put(id, doc);
                        idToCollectionMap.put(id, collectionName);
                    }
                });
                
                collectionCache.put(collectionName, cache);
                cachedCollectionNames.add(collectionName);
                
                long elapsed = System.currentTimeMillis() - startTime;
                logger.info("Cached {} {} documents in {}s", cache.size(), collectionName, elapsed / 1000);
                
                // Also inform RelationExpander about the cache if available
                if (relationExpander != null) {
                    relationExpander.preloadCollections(Collections.singleton(collectionName));
                }
                
            } catch (Exception e) {
                logger.warn("Failed to cache collection {}: {}", collectionName, e.getMessage());
            }
        }
    }
    
    /**
     * Expand fields from a related collection - FIXED to sample multiple documents
     */
    private void expandRelationFields(String fieldPath, String targetCollection) {
        try {
            MongoCollection<Document> collection = database.getCollection(targetCollection);
            
            // Sample MULTIPLE documents from the target collection to discover all fields
            // This fixes CRITICAL ISSUE 1 - was only sampling 1 document
            int sampleSize = Math.min(100, (int) collection.countDocuments());
            Set<String> expandedFields = new HashSet<>();
            
            for (Document sample : collection.find().limit(sampleSize)) {
                if (sample != null) {
                    // Discover fields in the related document
                    String expandedPrefix = fieldPath + ".@expanded";
                    int beforeCount = discoveredFieldPaths.size();
                    discoverFieldsInDocument(sample, expandedPrefix, 1);
                    int afterCount = discoveredFieldPaths.size();
                    
                    if (afterCount > beforeCount) {
                        expandedFields.addAll(
                            discoveredFieldPaths.stream()
                                .filter(p -> p.startsWith(expandedPrefix))
                                .collect(Collectors.toSet())
                        );
                    }
                }
            }
            
            logger.debug("    Added {} expanded fields from {} (sampled {} docs)", 
                expandedFields.size(), targetCollection, sampleSize);
        } catch (Exception e) {
            logger.debug("Could not expand {}: {}", targetCollection, e.getMessage());
        }
    }
    
    /**
     * Phase 3: Export with all discovered fields
     */
    private void exportWithAllFields() {
        exportWithStatistics(csvWriter -> {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            long totalDocs = collection.countDocuments();
            logger.info("Exporting {} documents with {} fields", totalDocs, includedFieldPaths.size());
            
            int processedCount = 0;
            long startTime = System.currentTimeMillis();
            
            // Process in larger batches for efficiency
            int batchSize = 5000; // Increased from 1000
            
            try (MongoCursor<Document> cursor = collection.find().batchSize(batchSize).iterator()) {
                List<Document> batch = new ArrayList<>(batchSize);
                
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    
                    if (batch.size() >= batchSize || !cursor.hasNext()) {
                        processBatch(batch, csvWriter);
                        processedCount += batch.size();
                        
                        // Progress logging
                        if (processedCount % 10000 == 0) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            double rate = processedCount / (elapsed / 1000.0);
                            long eta = (long)((totalDocs - processedCount) / rate);
                            logger.info("Progress: {} / {} documents ({} docs/sec, ETA: {}s)", 
                                processedCount, totalDocs, String.format("%.0f", rate), eta);
                        }
                        
                        batch.clear();
                    }
                }
            } catch (IOException e) {
                logger.error("Export failed", e);
            }
            
            logger.info("Export complete: Processed exactly {} documents", processedCount);
            return processedCount;
        });
    }
    
    private void processBatch(List<Document> batch, CSVWriter csvWriter) throws IOException {
        // Build headers once for the entire batch
        String[] headers = null;
        if (enableFieldStatistics) {
            headers = buildComprehensiveHeaders();
        }
        
        for (Document doc : batch) {
            try {
                // Expand relations if needed
                if (autoExpandRelations) {
                    doc = expandDocumentRelations(doc);
                }
                
                // Extract values for all discovered fields
                String[] row = extractValuesForAllFields(doc);
                
                // Verify row has correct number of columns
                if (row.length != includedFieldPaths.size()) {
                    logger.warn("Row has {} columns but expected {}", row.length, includedFieldPaths.size());
                }
                
                csvWriter.writeNext(row);
                
                if (enableFieldStatistics && headers != null) {
                    collectRowStatistics(row, headers);
                }
            } catch (Exception e) {
                logger.error("Error processing document {}: {}", doc.get("_id"), e.getMessage());
                // Don't write empty rows on error - skip the document instead
                logger.warn("Skipping document due to error");
            }
        }
    }
    
    /**
     * Expand all foreign key relationships in a document
     * Uses RelationExpander exclusively - no fallback
     */
    private Document expandDocumentRelations(Document doc) {
        if (relationExpander != null && autoExpandRelations) {
            try {
                return relationExpander.expandDocument(doc, collectionName, maxExpansionDepth);
            } catch (Exception e) {
                logger.error("RelationExpander failed for document {}: {}", 
                    doc.getObjectId("_id"), e.getMessage());
                return doc; // Return original if expansion fails
            }
        }
        return doc;
    }
    
    // DELETED: Entire fallback expansion logic removed
    // DELETED: lookupDocument method removed
    // DELETED: extractSortableLabel method removed
    // DELETED: setValueByPath method removed
    
    /**
     * Filter fields based on distinct non-null value criteria
     * FIXED: Now preserves business-critical IDs while excluding technical IDs
     */
    private void filterFieldsByDistinctValues() {
        // CRITICAL FIX: Define business IDs that MUST be kept
        Set<String> BUSINESS_IDS = new HashSet<>(Arrays.asList(
            "mlsNumber", "listingId", "transactionId", "orderId", 
            "contractNumber", "referenceNumber", "confirmationNumber",
            "invoiceNumber", "accountNumber", "caseNumber", "ticketId"
        ));
        
        includedFieldPaths.clear();
        int excluded = 0;
        int alwaysEmpty = 0;
        int binary = 0;
        int multiValue = 0;
        int expandedIncluded = 0;
        int businessIdsKept = 0;
        
        for (String fieldPath : discoveredFieldPaths) {
            Set<String> values = fieldPathValues.get(fieldPath);
            int distinctNonNullCount = (values != null) ? values.size() : 0;
            int nullCount = fieldPathNullCounts.getOrDefault(fieldPath, 0);
            int totalOccurrences = fieldPathCounts.getOrDefault(fieldPath, 0);
            
            // Calculate actual non-null occurrences
            int nonNullOccurrences = totalOccurrences - nullCount;
            
            // Skip fields that are always empty
            if (nonNullOccurrences == 0) {
                alwaysEmpty++;
                excluded++;
                logger.debug("Excluding always-empty field: {}", fieldPath);
                continue;
            }
            
            // Extract just the field name for business ID checking
            String fieldName = fieldPath.contains(".") ? 
                fieldPath.substring(fieldPath.lastIndexOf(".") + 1) : fieldPath;
            
            // Determine if field should be included
            boolean include = false;
            String reason = "";
            
            // FIRST: Check if this is a business ID that must be kept
            if (BUSINESS_IDS.contains(fieldName) && distinctNonNullCount > 0) {
                include = true;
                reason = "business ID";
                businessIdsKept++;
            }
            // EXCLUDE technical ID fields (but not business IDs)
            else if (fieldPath.equals("_id") || 
                fieldPath.equals("__v") ||
                fieldPath.contains("._id") ||
                fieldPath.contains(".@reference") ||
                (fieldPath.endsWith("Id") && !BUSINESS_IDS.contains(fieldName))) {
                include = false;
                reason = "technical ID excluded";
            }
            // Always include certain important fields (IF they have 2+ distinct values)
            else if ((fieldPath.contains("Date") ||
                     fieldPath.contains("Price") ||
                     fieldPath.contains("Amount")) && distinctNonNullCount >= minDistinctNonNullValues) {
                include = true;
                reason = "important field";
            }
            // CONSISTENT RULE: Include ANY field with 2+ distinct non-null values
            // No special treatment for expanded fields - they follow the same rule
            else if (distinctNonNullCount >= minDistinctNonNullValues) {
                include = true;
                if (distinctNonNullCount == 2) {
                    binary++;
                    reason = "binary field";
                } else {
                    multiValue++;
                    reason = "multi-value field";
                }
                // Track if it's an expanded field for statistics
                if (fieldPath.contains("@expanded")) {
                    expandedIncluded++;
                }
            }
            // Exclude if field has 0 or 1 distinct non-null values
            else {
                include = false;
                reason = distinctNonNullCount == 0 ? "no values" : "single value";
            }
            
            if (include) {
                includedFieldPaths.add(fieldPath);
                logger.debug("Including field: {} ({}, distinct non-null: {})", 
                    fieldPath, reason, distinctNonNullCount);
            } else {
                excluded++;
                logger.debug("Excluding field: {} ({}, distinct non-null: {})", 
                    fieldPath, reason, distinctNonNullCount);
            }
        }
        
        logger.info("Field filtering complete: {} included ({} business IDs, {} binary, {} multi-value, {} expanded), {} excluded ({} always-empty) from {} total", 
            includedFieldPaths.size(), businessIdsKept, binary, multiValue, expandedIncluded, excluded, alwaysEmpty, discoveredFieldPaths.size());
    }
    
    /**
     * Extract values for all discovered field paths
     */
    private String[] extractValuesForAllFields(Document doc) {
        // Use filtered fields, not all discovered fields
        String[] values = new String[includedFieldPaths.size()];
        int index = 0;
        
        for (String fieldPath : includedFieldPaths) {
            Object value = getValueByPath(doc, fieldPath);
            values[index++] = formatValue(value);
        }
        
        return values;
    }
    
    /**
     * Get value from document by dot-notation path
     * Arrays are concatenated with semicolons to ensure one row per document
     */
    private Object getValueByPath(Document doc, String path) {
        if (doc == null || path == null) return null;
        
        // Handle array notation
        path = path.replace("[]", "");
        
        String[] parts = path.split("\\.");
        Object current = doc;
        
        for (String part : parts) {
            if (current == null) return null;
            
            if (current instanceof Document) {
                current = ((Document) current).get(part);
            } else if (current instanceof List) {
                List<?> list = (List<?>) current;
                if (!list.isEmpty()) {
                    // Always concatenate arrays to ensure one row per document
                    if (part.equals("@expanded") || parts[parts.length - 1].equals(part)) {
                        // This is the final part or an expanded field - return the list for formatting
                        return list;
                    } else {
                        // Extract values from all items, sort, and concatenate
                        List<String> values = new ArrayList<>();
                        for (Object item : list) {
                            if (item instanceof Document) {
                                Object val = ((Document) item).get(part);
                                if (val != null) {
                                    values.add(formatValue(val));
                                }
                            } else {
                                values.add(formatValue(item));
                            }
                        }
                        // Sort values for consistent output
                        Collections.sort(values);
                        return values.isEmpty() ? null : String.join("; ", values);
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    
    /**
     * Format a value for CSV output
     * Ensures arrays are concatenated and dates are properly formatted
     */
    private String formatValue(Object value) {
        if (value == null) return "";
        
        if (value instanceof Document) {
            // For nested documents, extract meaningful fields (no IDs)
            Document doc = (Document) value;
            
            // Try to find a meaningful label field
            String[] labelFields = {"name", "title", "label", "displayName", "fullName", 
                                   "firstName", "lastName", "description", "address", 
                                   "email", "phone", "type", "category", "status"};
            
            for (String field : labelFields) {
                String val = doc.getString(field);
                if (val != null && !val.isEmpty()) {
                    // Clean the value of any newlines
                    val = val.replaceAll("\\r\\n|\\r|\\n", " ")
                            .replaceAll("\\u2028|\\u2029", " ")
                            .replaceAll("\\s+", " ")
                            .trim();
                    
                    // For firstName/lastName, combine them
                    if (field.equals("firstName")) {
                        String lastName = doc.getString("lastName");
                        if (lastName != null) {
                            lastName = lastName.replaceAll("\\r\\n|\\r|\\n", " ")
                                              .replaceAll("\\s+", " ")
                                              .trim();
                            return val + " " + lastName;
                        }
                    }
                    return val;
                }
            }
            
            // If no label fields found, return non-ID fields as key:value pairs
            // But check if this is an @expanded document - if so, just return "@expanded"
            if (doc.containsKey("@expanded")) {
                return "@expanded";
            }
            return doc.entrySet().stream()
                .filter(e -> !e.getKey().equals("_id") && !e.getKey().endsWith("Id"))
                .limit(2)
                .map(e -> {
                    Object val = e.getValue();
                    // Avoid recursive issues with Documents
                    if (val instanceof Document) {
                        return e.getKey() + ":...";
                    }
                    return e.getKey() + ":" + formatValue(val);
                })
                .collect(Collectors.joining(","));
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) return "";
            
            // Concatenate with semicolon to ensure single row
            int maxItems = 10;
            
            // Format values first
            List<String> formattedValues = new ArrayList<>();
            for (Object item : list) {
                String formatted = formatValue(item);
                if (!formatted.isEmpty()) {
                    formattedValues.add(formatted);
                }
            }
            
            // Sort for consistent output (so same sets always match)
            Collections.sort(formattedValues);
            
            // Build result string
            StringBuilder result = new StringBuilder();
            int count = 0;
            for (String val : formattedValues) {
                if (count >= maxItems) {
                    result.append("; ...").append(formattedValues.size() - maxItems).append(" more");
                    break;
                }
                if (count > 0) result.append("; ");
                result.append(val);
                count++;
            }
            return result.toString();
        } else if (value instanceof Date) {
            // Format dates consistently
            return DATE_FORMAT.format((Date) value);
        } else if (value instanceof ObjectId) {
            return value.toString();
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Number) {
            // Format numbers appropriately
            if (value instanceof Double || value instanceof Float) {
                double d = ((Number) value).doubleValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return String.format("%.0f", d);
                } else {
                    return String.format("%.2f", d);
                }
            }
            return value.toString();
        } else {
            // Remove ALL types of newlines and carriage returns to ensure one row per document
            String str = value.toString();
            
            // First handle HTML breaks
            str = str.replaceAll("<br\\s*/?>", " ")      // HTML breaks
                    .replaceAll("<BR\\s*/?>", " ")        // Capital HTML breaks
                    .replaceAll("</?(p|P|div|DIV)>", " ") // Paragraph and div tags
                    .replaceAll("<[^>]+>", " ");          // Remove all other HTML tags
            
            // Then replace all possible line breaks with spaces
            str = str.replaceAll("\\r\\n|\\r|\\n", " ")  // Windows, Mac, Unix line breaks
                    .replaceAll("\\u2028|\\u2029", " ")   // Unicode line/paragraph separators
                    .replaceAll("\\u000B|\\u000C|\\u0085", " ") // Vertical tab, form feed, next line
                    .replaceAll("\\s+", " ")              // Collapse multiple spaces
                    .trim();
            
            // Escape quotes to prevent CSV issues
            if (str.contains("\"")) {
                str = str.replace("\"", "\"\"");  // Double quotes for CSV escaping
            }
            
            return str;
        }
    }
    
    @Override
    protected String[] buildComprehensiveHeaders() {
        // Convert filtered field paths to business names
        List<String> headers = new ArrayList<>();
        
        for (String fieldPath : includedFieldPaths) {
            // Convert to business name if enabled
            String headerName = useBusinessNames ? 
                FieldNameMapper.getBusinessName(fieldPath) : fieldPath;
            headers.add(headerName);
        }
        
        logger.debug("Generated {} headers with business names", headers.size());
        return headers.toArray(new String[0]);
    }
    
    @Override
    protected void loadCollectionsIntoMemory() {
        // No longer used - collections are cached dynamically during discovery
        // Left empty to satisfy abstract class requirement
    }
    
    // Process a document for export
    protected String[] processDocument(Document doc) {
        return extractValuesForAllFields(doc);
    }
    
    /**
     * Phase 4: Generate comprehensive field report
     */
    private void generateFieldReport() {
        logger.info("Generating field discovery report...");
        
        Map<String, Object> report = new HashMap<>();
        report.put("collection", collectionName);
        report.put("totalFieldsDiscovered", discoveredFieldPaths.size());
        report.put("fieldsIncludedInExport", includedFieldPaths.size());
        report.put("fieldsExcluded", discoveredFieldPaths.size() - includedFieldPaths.size());
        report.put("minDistinctNonNullValuesRequired", minDistinctNonNullValues);
        report.put("expansionDepth", maxExpansionDepth);
        report.put("useBusinessNames", useBusinessNames);
        report.put("timestamp", new Date());
        
        // Categorize fields
        List<Map<String, Object>> fieldDetails = new ArrayList<>();
        for (String fieldPath : discoveredFieldPaths) {
            Map<String, Object> fieldInfo = new HashMap<>();
            fieldInfo.put("path", fieldPath);
            fieldInfo.put("occurrences", fieldPathCounts.getOrDefault(fieldPath, 0));
            fieldInfo.put("isReference", fieldPath.contains("@reference"));
            fieldInfo.put("isExpanded", fieldPath.contains("@expanded"));
            fieldInfo.put("isArray", fieldPath.contains("[]"));
            fieldInfo.put("depth", fieldPath.split("\\.").length);
            
            // Add sample values if available
            Set<String> samples = fieldPathValues.get(fieldPath);
            if (samples != null && !samples.isEmpty()) {
                fieldInfo.put("sampleValues", samples.stream().limit(5).collect(Collectors.toList()));
                fieldInfo.put("uniqueValues", samples.size());
            }
            
            // Mark if field was included in export
            fieldInfo.put("includedInExport", includedFieldPaths.contains(fieldPath));
            
            // Add business name if included
            if (includedFieldPaths.contains(fieldPath) && useBusinessNames) {
                fieldInfo.put("businessName", FieldNameMapper.getBusinessName(fieldPath));
            }
            
            fieldDetails.add(fieldInfo);
        }
        
        report.put("fields", fieldDetails);
        
        // Save the report
        String reportPath = config.getOutputDirectory() + "/" + collectionName + "_discovery_report.json";
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(new java.io.File(reportPath), report);
            logger.info("Discovery report saved to: {}", reportPath);
        } catch (IOException e) {
            logger.error("Failed to save discovery report", e);
        }
        
        // Log summary
        long referenceFields = discoveredFieldPaths.stream().filter(p -> p.contains("@reference")).count();
        long expandedFields = discoveredFieldPaths.stream().filter(p -> p.contains("@expanded")).count();
        long arrayFields = discoveredFieldPaths.stream().filter(p -> p.contains("[]")).count();
        
        logger.info("\n=== DISCOVERY SUMMARY ===");
        logger.info("Total fields discovered: {}", discoveredFieldPaths.size());
        logger.info("Reference fields: {}", referenceFields);
        logger.info("Expanded fields: {}", expandedFields);
        logger.info("Array fields: {}", arrayFields);
        logger.info("Max depth reached: {}", 
            discoveredFieldPaths.stream()
                .mapToInt(p -> p.split("\\.").length)
                .max()
                .orElse(0));
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            logger.error("Usage: AutoDiscoveryExporter <collection>");
            System.exit(1);
        }
        
        String collection = args[0];
        ExportOptions options = ExportOptions.builder()
            .enableFieldStatistics(true)
            .enableRelationExpansion(true)
            .expansionDepth(3)
            .build();
        
        AutoDiscoveryExporter exporter = new AutoDiscoveryExporter(collection, options);
        exporter.export();
    }
}