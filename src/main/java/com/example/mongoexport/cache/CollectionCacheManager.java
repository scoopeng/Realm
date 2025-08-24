package com.example.mongoexport.cache;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Centralized collection caching manager used by both discovery and export phases.
 * Single source of truth for caching logic and thresholds.
 */
public class CollectionCacheManager {
    private static final Logger logger = LoggerFactory.getLogger(CollectionCacheManager.class);
    
    // Single configuration for cache threshold - used by both phases
    private static final long MAX_CACHEABLE_SIZE = 1_000_000; // 1M documents
    
    private final MongoDatabase database;
    private final Map<String, Map<ObjectId, Document>> collectionCache = new HashMap<>();
    private final Set<String> cachedCollectionNames = new HashSet<>();
    private final Set<String> fullyLoadedCollections = new HashSet<>();
    
    public CollectionCacheManager(MongoDatabase database) {
        this.database = database;
    }
    
    /**
     * Check if a collection should be cached based on size.
     * Single source of truth for caching decision.
     */
    public static boolean shouldCacheCollection(long documentCount) {
        return documentCount <= MAX_CACHEABLE_SIZE;
    }
    
    /**
     * Get the maximum cacheable collection size.
     */
    public static long getMaxCacheableSize() {
        return MAX_CACHEABLE_SIZE;
    }
    
    /**
     * Cache a collection if it meets size requirements.
     * Returns true if collection was fully cached, false if lazy loading will be used.
     * 
     * @param collectionName The collection to cache
     * @param skipIfSource If true, skip caching if this is the source collection being exported
     * @param sourceCollection The source collection name (for comparison with skipIfSource)
     * @return true if fully cached, false if using lazy loading
     */
    public boolean cacheCollection(String collectionName, boolean skipIfSource, String sourceCollection) {
        if (cachedCollectionNames.contains(collectionName)) {
            return fullyLoadedCollections.contains(collectionName);
        }
        
        // Never cache the source collection being exported
        if (skipIfSource && collectionName.equals(sourceCollection)) {
            logger.info("Skipping cache for source collection {}", collectionName);
            return false;
        }
        
        // Check if collection exists
        boolean exists = database.listCollectionNames().into(new ArrayList<>()).contains(collectionName);
        if (!exists) {
            logger.warn("Collection {} does not exist, skipping", collectionName);
            return false;
        }
        
        MongoCollection<Document> collection = database.getCollection(collectionName);
        long count = collection.estimatedDocumentCount();
        
        if (shouldCacheCollection(count)) {
            logger.info("Caching collection {} ({} documents)", collectionName, count);
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
            cachedCollectionNames.add(collectionName);
            fullyLoadedCollections.add(collectionName);
            logger.info("  Cached {} documents (fully loaded)", cache.size());
            return true;
        } else {
            logger.info("Collection {} too large ({} docs), will use lazy loading", collectionName, count);
            cachedCollectionNames.add(collectionName);
            // Create empty cache for lazy loading
            collectionCache.put(collectionName, new HashMap<>());
            return false;
        }
    }
    
    /**
     * Get a cached document by ID.
     * If collection is partially cached and document not found, attempts lazy loading.
     */
    public Document getCachedDocument(String collectionName, ObjectId id) {
        Map<ObjectId, Document> cache = collectionCache.get(collectionName);
        if (cache == null) {
            return null;
        }
        
        Document doc = cache.get(id);
        if (doc != null) {
            return doc;
        }
        
        // If not fully loaded, try lazy loading
        if (cachedCollectionNames.contains(collectionName) && !fullyLoadedCollections.contains(collectionName)) {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            doc = collection.find(new Document("_id", id)).first();
            if (doc != null) {
                cache.put(id, doc);
            }
            return doc;
        }
        
        return null;
    }
    
    /**
     * Get the cache for a collection (may be empty for lazy-loaded collections).
     */
    public Map<ObjectId, Document> getCollectionCache(String collectionName) {
        return collectionCache.get(collectionName);
    }
    
    /**
     * Check if a collection is cached (either fully or partially).
     */
    public boolean isCached(String collectionName) {
        return cachedCollectionNames.contains(collectionName);
    }
    
    /**
     * Check if a collection is fully loaded in memory.
     */
    public boolean isFullyLoaded(String collectionName) {
        return fullyLoadedCollections.contains(collectionName);
    }
    
    /**
     * Get statistics about cached collections.
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cachedCollections", cachedCollectionNames.size());
        stats.put("fullyLoadedCollections", fullyLoadedCollections.size());
        stats.put("totalCachedDocuments", 
            collectionCache.values().stream().mapToInt(Map::size).sum());
        stats.put("collections", cachedCollectionNames);
        return stats;
    }
    
    /**
     * Get all collection caches (for RelationExpander compatibility).
     */
    public Map<String, Map<ObjectId, Document>> getAllCaches() {
        return collectionCache;
    }
    
    /**
     * Batch load documents for a collection.
     */
    public void batchLoadDocuments(String collectionName, Set<ObjectId> ids) {
        if (!cachedCollectionNames.contains(collectionName)) {
            return;
        }
        
        Map<ObjectId, Document> cache = collectionCache.get(collectionName);
        if (cache == null) {
            return;
        }
        
        // Find IDs that aren't already cached
        Set<ObjectId> missingIds = new HashSet<>();
        for (ObjectId id : ids) {
            if (!cache.containsKey(id)) {
                missingIds.add(id);
            }
        }
        
        // Batch load missing documents
        if (!missingIds.isEmpty()) {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            Document query = new Document("_id", new Document("$in", new ArrayList<>(missingIds)));
            
            try (MongoCursor<Document> cursor = collection.find(query).iterator()) {
                int loaded = 0;
                while (cursor.hasNext()) {
                    Document doc = cursor.next();
                    Object id = doc.get("_id");
                    if (id instanceof ObjectId) {
                        cache.put((ObjectId) id, doc);
                        loaded++;
                    }
                }
                
                if (loaded > 0) {
                    logger.debug("Batch loaded {} documents from {} collection", loaded, collectionName);
                }
            }
        }
    }
}