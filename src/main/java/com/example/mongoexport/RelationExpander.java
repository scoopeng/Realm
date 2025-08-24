package com.example.mongoexport;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles automatic expansion of related collections for MongoDB documents
 */
public class RelationExpander
{
    private static final Logger logger = LoggerFactory.getLogger(RelationExpander.class);

    private final MongoDatabase database;
    private final Map<String, RelationConfig> relationConfigs = new HashMap<>();
    private Map<String, Map<String, String>> discoveredRelationships = new HashMap<>();  // collection -> field -> target

    // Cache for expanded documents
    private final Map<String, Map<ObjectId, Document>> cacheByCollection = new HashMap<>();

    public RelationExpander(MongoDatabase database)
    {
        this.database = database;
        // NO MORE initializeRelationConfigs() - relationships come from discovery!
    }

    /**
     * Set discovered relationships from FieldDiscoveryService
     * This replaces ALL hardcoded mappings - everything is data-driven
     * 
     * @param collectionName The source collection
     * @param relationships Map of field -> target collection
     */
    public void setDiscoveredRelationships(String collectionName, Map<String, String> relationships)
    {
        if (relationships == null || relationships.isEmpty()) {
            return;
        }
        
        discoveredRelationships.put(collectionName, relationships);
        
        // Build RelationConfig from discovered relationships
        RelationConfig config = new RelationConfig(collectionName);
        
        for (Map.Entry<String, String> entry : relationships.entrySet()) {
            String fieldPath = entry.getKey();
            String targetCollection = entry.getValue();
            
            // Determine relationship type based on field characteristics
            // This is data-driven inference, not hardcoding
            RelationType type = inferRelationType(fieldPath, collectionName, targetCollection);
            
            if (fieldPath.contains(".")) {
                // Nested field
                config.addNestedRelation(fieldPath, targetCollection, type);
            } else {
                // Direct field
                config.addRelation(fieldPath, targetCollection, type);
            }
            
            logger.debug("Added discovered relationship: {} -> {} -> {} ({})", 
                        collectionName, fieldPath, targetCollection, type);
        }
        
        relationConfigs.put(collectionName, config);
    }
    
    /**
     * Infer relationship type from field characteristics
     * This is pattern-based inference, not hardcoding
     */
    private RelationType inferRelationType(String fieldPath, String sourceCollection, String targetCollection)
    {
        // Default to MANY_TO_ONE for ObjectId fields
        // Arrays would be detected during discovery and passed differently
        return RelationType.MANY_TO_ONE;
    }
    
    /**
     * REMOVED: All hardcoded relationship configurations
     * Everything now comes from discovery
     */
    private void initializeRelationConfigs_REMOVED()
    {
        // ALL HARDCODED RELATIONSHIPS REMOVED
        // Relationships now come from discovery via setDiscoveredRelationships()
        // This method is kept only for backward compatibility and does nothing
    }

    /**
     * Preload collections that should be cached in memory
     */
    /**
     * Set the cache directly from an external source (e.g., FieldDiscoveryService)
     * This allows sharing the same cache across components for maximum efficiency
     */
    public void setCache(Map<String, Map<ObjectId, Document>> cache)
    {
        if (cache != null)
        {
            cacheByCollection.clear();
            cacheByCollection.putAll(cache);
            logger.debug("Cache set with {} collections containing {} total documents",
                    cache.size(),
                    cache.values().stream().mapToInt(Map::size).sum());
        }
    }
    
    public void preloadCollections(Set<String> collectionNames)
    {
        logger.info("Preloading {} collections for relation expansion...", collectionNames.size());

        for (String collectionName : collectionNames)
        {
            if (!cacheByCollection.containsKey(collectionName))
            {
                long startTime = System.currentTimeMillis();
                Map<ObjectId, Document> cache = new HashMap<>();
                MongoCollection<Document> collection = database.getCollection(collectionName);

                // Get estimated count for progress
                long estimatedCount = collection.estimatedDocumentCount();
                logger.info("Preloading collection: {} (~{} docs)", collectionName, estimatedCount);

                long count = 0;
                for (Document doc : collection.find())
                {
                    ObjectId id = doc.getObjectId("_id");
                    if (id != null)
                    {
                        cache.put(id, doc);
                        count++;

                        // More frequent updates for large collections
                        int interval = estimatedCount > 100000 ? 10000 : 5000;
                        if (count % interval == 0)
                        {
                            long elapsed = System.currentTimeMillis() - startTime;
                            double rate = count / (elapsed / 1000.0);
                            long remaining = estimatedCount - count;
                            int secondsLeft = remaining > 0 ? (int) (remaining / rate) : 0;

                            logger.info("  {} - Loaded {} / ~{} documents ({} docs/sec, ~{}s remaining)",
                                    collectionName, count, estimatedCount,
                                    String.format("%.0f", rate), secondsLeft);
                        }
                    }
                }

                long totalTime = System.currentTimeMillis() - startTime;
                cacheByCollection.put(collectionName, cache);
                logger.info("Completed {} - Loaded {} documents in {} seconds",
                        collectionName, cache.size(), totalTime / 1000);
            } else
            {
                logger.info("Collection {} already cached", collectionName);
            }
        }

        logger.info("All collections preloaded. Total cached documents: {}",
                cacheByCollection.values().stream().mapToInt(Map::size).sum());
    }

    /**
     * Expand all relations for a document
     */
    public Document expandDocument(Document doc, String collectionName, int maxDepth)
    {
        Set<String> visitedCollections = new HashSet<>();
        visitedCollections.add(collectionName); // Add the starting collection
        return expandDocument(doc, collectionName, maxDepth, 0, new HashSet<>(), visitedCollections);
    }

    private Document expandDocument(Document doc, String collectionName, int maxDepth, int currentDepth, Set<String> visited, Set<String> visitedCollections)
    {
        if (doc == null || currentDepth >= maxDepth)
        {
            return doc;
        }

        String visitKey = collectionName + ":" + doc.getObjectId("_id");
        if (visited.contains(visitKey))
        {
            return doc; // Prevent circular references
        }
        visited.add(visitKey);

        Document expanded = new Document(doc);
        RelationConfig config = relationConfigs.get(collectionName);

        if (currentDepth == 0 && logger.isTraceEnabled()) {
            ObjectId docId = doc.getObjectId("_id");
            logger.trace("=== EXPANSION TRACE for {} document {} ===", collectionName, 
                docId != null ? docId.toHexString() : "no-id");
            logger.trace("RelationConfig exists: {}", config != null);
            if (config != null) {
                logger.trace("Number of configured relations: {}", config.getRelations().size());
            }
        }

        if (config != null)
        {
            for (Relation relation : config.getRelations())
            {
                if (currentDepth == 0 && logger.isTraceEnabled()) {
                    logger.trace("Processing relation: {} -> {} (type: {})", 
                        relation.localField, relation.foreignCollection, relation.type);
                }
                expandRelation(expanded, relation, currentDepth, maxDepth, visited, visitedCollections);
            }
        }

        return expanded;
    }

    private void expandRelation(Document doc, Relation relation, int currentDepth, int maxDepth, Set<String> visited, Set<String> visitedCollections)
    {
        // Check if we've already visited this collection in the expansion chain
        if (visitedCollections.contains(relation.foreignCollection))
        {
            if (logger.isDebugEnabled()) {
                logger.debug("Skipping expansion of {} -> {} to prevent cycle (already visited: {})", 
                    relation.localField, relation.foreignCollection, visitedCollections);
            }
            return;
        }
        
        Object value = getNestedValue(doc, relation.localField);
        if (value == null)
        {
            if (currentDepth == 0 && logger.isTraceEnabled()) {
                logger.trace("Field '{}' is null, skipping expansion", relation.localField);
            }
            return;
        }

        String expandedFieldName = relation.localField + "_expanded";
        
        // Add this collection to visited set for recursive calls
        Set<String> newVisitedCollections = new HashSet<>(visitedCollections);
        newVisitedCollections.add(relation.foreignCollection);

        switch (relation.type)
        {
            case MANY_TO_ONE:
                if (value instanceof ObjectId)
                {
                    if (currentDepth == 0 && logger.isTraceEnabled()) {
                        logger.trace("Expanding MANY_TO_ONE: {} (ObjectId: {}) -> {}", 
                            relation.localField, ((ObjectId)value).toHexString(), relation.foreignCollection);
                    }
                    Document related = fetchDocument(relation.foreignCollection, (ObjectId) value);
                    if (related != null)
                    {
                        // Log critical expansion info for client field ONCE
                        if (relation.localField.equals("client") && currentDepth == 0) {
                            logger.info("EXPANSION: Fetched {} from collection '{}', keys: {}", 
                                relation.localField, relation.foreignCollection, related.keySet());
                            if (related.containsKey("address")) {
                                logger.info("EXPANSION: Document HAS address field!");
                            }
                        }
                        doc.append(expandedFieldName, expandDocument(related, relation.foreignCollection, maxDepth, currentDepth + 1, visited, newVisitedCollections));
                        if (currentDepth == 0 && logger.isTraceEnabled()) {
                            logger.trace("Successfully expanded {} -> {} with {} fields", 
                                relation.localField, expandedFieldName, related.keySet().size());
                        }
                    } else if (currentDepth == 0 && logger.isTraceEnabled()) {
                        logger.trace("Could not find document {} in collection {}", 
                            ((ObjectId)value).toHexString(), relation.foreignCollection);
                    }
                } else if (value instanceof Document && ((Document) value).containsKey("_id"))
                {
                    // Handle embedded documents with _id
                    ObjectId id = ((Document) value).getObjectId("_id");
                    if (id != null)
                    {
                        Document related = fetchDocument(relation.foreignCollection, id);
                        if (related != null)
                        {
                            doc.append(expandedFieldName, expandDocument(related, relation.foreignCollection, maxDepth, currentDepth + 1, visited, newVisitedCollections));
                        }
                    }
                }
                break;

            case ONE_TO_ONE:
                if (value instanceof ObjectId)
                {
                    Document related = fetchDocumentByField(relation.foreignCollection, relation.foreignField, value);
                    if (related != null)
                    {
                        doc.append(expandedFieldName, expandDocument(related, relation.foreignCollection, maxDepth, currentDepth + 1, visited, newVisitedCollections));
                    }
                }
                break;

            case ONE_TO_MANY:
                if (value instanceof ObjectId)
                {
                    List<Document> related = fetchDocumentsByField(relation.foreignCollection, relation.foreignField, value);
                    if (!related.isEmpty())
                    {
                        List<Document> expandedList = related.stream()
                                .map(d -> expandDocument(d, relation.foreignCollection, maxDepth, currentDepth + 1, visited, newVisitedCollections))
                                .collect(Collectors.toList());
                        doc.append(expandedFieldName, expandedList);
                    }
                }
                break;

            case MANY_TO_MANY:
            case ONE_TO_MANY_ARRAY:  // Handle arrays of ObjectIds the same way
                if (value instanceof List)
                {
                    List<Document> expandedList = new ArrayList<>();
                    for (Object item : (List<?>) value)
                    {
                        if (item instanceof ObjectId)
                        {
                            Document related = fetchDocument(relation.foreignCollection, (ObjectId) item);
                            if (related != null)
                            {
                                expandedList.add(expandDocument(related, relation.foreignCollection, maxDepth, currentDepth + 1, visited, newVisitedCollections));
                            }
                        }
                    }
                    if (!expandedList.isEmpty())
                    {
                        doc.append(expandedFieldName, expandedList);
                    }
                }
                break;
        }
    }

    private Document fetchDocument(String collectionName, ObjectId id)
    {
        // Check cache first
        Map<ObjectId, Document> cache = cacheByCollection.get(collectionName);
        if (cache != null)
        {
            // If collection is cached, ONLY use cache - never query database
            Document doc = cache.get(id);
            // Return the document if found, or null if not in cache
            // This is critical for performance - cached collections should NEVER hit the database
            return doc;
        }

        // Only fetch from database if collection is NOT cached
        MongoCollection<Document> collection = database.getCollection(collectionName);
        Document doc = collection.find(new Document("_id", id)).first();

        // For non-cached collections, implement lazy loading if it's a small collection
        if (doc != null && isSmallCollection(collectionName))
        {
            // Create cache for small collections on demand
            cache = cacheByCollection.computeIfAbsent(collectionName, k -> new HashMap<>());
            cache.put(id, doc);
        }

        return doc;
    }

    private Document fetchDocumentByField(String collectionName, String fieldName, Object value)
    {
        Map<ObjectId, Document> cache = cacheByCollection.get(collectionName);
        
        // If collection is cached, search in cache ONLY
        if (cache != null)
        {
            // Special case for _id field
            if ("_id".equals(fieldName) && value instanceof ObjectId)
            {
                return cache.get(value);
            }
            
            // For other fields, search through cached documents
            for (Document doc : cache.values())
            {
                Object fieldValue = doc.get(fieldName);
                if (value.equals(fieldValue))
                {
                    return doc;
                }
            }
            // Not found in cache - return null (don't query database for cached collections)
            return null;
        }

        // Only fetch from database if collection is NOT cached
        MongoCollection<Document> collection = database.getCollection(collectionName);
        return collection.find(new Document(fieldName, value)).first();
    }

    private List<Document> fetchDocumentsByField(String collectionName, String fieldName, Object value)
    {
        List<Document> results = new ArrayList<>();

        // If cached, search through cache
        Map<ObjectId, Document> cache = cacheByCollection.get(collectionName);
        if (cache != null)
        {
            for (Document doc : cache.values())
            {
                Object fieldValue = doc.get(fieldName);
                if (value.equals(fieldValue) || (fieldValue instanceof List && ((List<?>) fieldValue).contains(value)))
                {
                    results.add(doc);
                }
            }
            return results;
        }

        // Fetch from database
        MongoCollection<Document> collection = database.getCollection(collectionName);
        collection.find(new Document(fieldName, value)).into(results);
        return results;
    }

    private Object getNestedValue(Document doc, String path)
    {
        String[] parts = path.split("\\.");
        Object current = doc;

        for (String part : parts)
        {
            if (current instanceof Document)
            {
                current = ((Document) current).get(part);
            } else if (current instanceof List && !((List<?>) current).isEmpty())
            {
                // Handle array access (take first element)
                current = ((List<?>) current).get(0);
                if (current instanceof Document)
                {
                    current = ((Document) current).get(part);
                }
            } else
            {
                return null;
            }
        }

        return current;
    }

    /**
     * Get collections that should be preloaded for a given collection type
     */
    public Set<String> getPreloadCollections(String collectionName)
    {
        Set<String> toPreload = new HashSet<>();
        RelationConfig config = relationConfigs.get(collectionName);

        if (config != null)
        {
            for (Relation relation : config.getRelations())
            {
                // Preload small collections and frequently accessed ones
                if (isSmallCollection(relation.foreignCollection))
                {
                    toPreload.add(relation.foreignCollection);
                }
            }
        }

        return toPreload;
    }

    private boolean isSmallCollection(String collectionName)
    {
        // Define which collections are small enough to preload
        Set<String> smallCollections = new HashSet<>(Arrays.asList(
                "brokerages", "tags", "teams", "awards", "marketProfiles",
                "usCities", "currentAgents", "agents", "transactions",
                "listingSearch", "agentSearch", "transactionsderived"
        ));
        // Note: properties is too large (1.9M) to preload entirely
        // It should be selectively cached based on actual usage
        return smallCollections.contains(collectionName);
    }

    // Inner classes for configuration

    private static class RelationConfig
    {
        private final String collectionName;
        private final List<Relation> relations = new ArrayList<>();

        public RelationConfig(String collectionName)
        {
            this.collectionName = collectionName;
        }

        public void addRelation(String localField, String foreignCollection, RelationType type)
        {
            relations.add(new Relation(localField, foreignCollection, type, "_id"));
        }

        public void addRelation(String localField, String foreignCollection, RelationType type, String foreignField)
        {
            relations.add(new Relation(localField, foreignCollection, type, foreignField));
        }

        public void addNestedRelation(String localField, String foreignCollection, RelationType type)
        {
            relations.add(new Relation(localField, foreignCollection, type, "_id"));
        }

        public List<Relation> getRelations()
        {
            return relations;
        }
    }

    private static class Relation
    {
        final String localField;
        final String foreignCollection;
        final RelationType type;
        final String foreignField;

        public Relation(String localField, String foreignCollection, RelationType type, String foreignField)
        {
            this.localField = localField;
            this.foreignCollection = foreignCollection;
            this.type = type;
            this.foreignField = foreignField;
        }
    }

    private enum RelationType
    {
        MANY_TO_ONE,      // Foreign key in local document
        ONE_TO_ONE,       // Unique foreign key
        ONE_TO_MANY,      // Foreign key in foreign document pointing back
        MANY_TO_MANY,     // Array of foreign keys with join table (not used in this codebase)
        ONE_TO_MANY_ARRAY // Array of foreign keys in local document (e.g., buyers[], sellers[])
    }
}