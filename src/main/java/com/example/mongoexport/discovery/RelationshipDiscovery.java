package com.example.mongoexport.discovery;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automatically discovers relationships between collections by testing ObjectIds
 * NO HARDCODING - learns from actual data
 */
public class RelationshipDiscovery {
    private static final Logger logger = LoggerFactory.getLogger(RelationshipDiscovery.class);
    
    private final MongoDatabase database;
    private final Map<String, String> discoveredRelationships = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> fieldToCollectionsMap = new ConcurrentHashMap<>();
    
    public RelationshipDiscovery(MongoDatabase database) {
        this.database = database;
    }
    
    /**
     * Discover which collection an ObjectId field references
     * by testing sample IDs against all collections
     */
    public String discoverTargetCollection(String fieldName, Set<ObjectId> sampleIds) {
        if (sampleIds == null || sampleIds.isEmpty()) {
            return null;
        }
        
        // Check if we already discovered this relationship
        String cached = discoveredRelationships.get(fieldName);
        if (cached != null) {
            return cached;
        }
        
        logger.debug("Discovering target collection for field '{}' with {} sample IDs", 
                    fieldName, sampleIds.size());
        
        // Get all collection names
        List<String> collectionNames = database.listCollectionNames().into(new ArrayList<>());
        
        // Test each collection to see if it contains these IDs
        Map<String, Integer> matchCounts = new HashMap<>();
        
        for (String collectionName : collectionNames) {
            try {
                MongoCollection<Document> collection = database.getCollection(collectionName);
                
                // Test a few sample IDs (not all, for performance)
                int matches = 0;
                int tested = 0;
                for (ObjectId id : sampleIds) {
                    if (tested >= 3) break; // Test up to 3 IDs per collection
                    
                    Document found = collection.find(new Document("_id", id)).first();
                    if (found != null) {
                        matches++;
                    }
                    tested++;
                }
                
                if (matches > 0) {
                    matchCounts.put(collectionName, matches);
                    logger.debug("  Found {} matches in collection '{}'", matches, collectionName);
                }
                
            } catch (Exception e) {
                // Skip collections we can't query
                logger.trace("Skipping collection '{}': {}", collectionName, e.getMessage());
            }
        }
        
        // Find the collection with the most matches
        String bestMatch = null;
        int maxMatches = 0;
        
        for (Map.Entry<String, Integer> entry : matchCounts.entrySet()) {
            if (entry.getValue() > maxMatches) {
                maxMatches = entry.getValue();
                bestMatch = entry.getKey();
            }
        }
        
        if (bestMatch != null) {
            logger.info("Field '{}' references collection '{}' (found {} matches)", 
                       fieldName, bestMatch, maxMatches);
            discoveredRelationships.put(fieldName, bestMatch);
            
            // Track this discovery for potential patterns
            fieldToCollectionsMap.computeIfAbsent(normalizeFieldName(fieldName), 
                k -> new HashSet<>()).add(bestMatch);
        } else {
            logger.debug("Could not determine target collection for field '{}'", fieldName);
        }
        
        return bestMatch;
    }
    
    /**
     * For arrays of embedded documents with an ObjectId field,
     * discover which collection that ObjectId references
     */
    public String discoverArrayRelationship(String arrayFieldName, 
                                           String objectIdFieldName, 
                                           Set<ObjectId> sampleIds) {
        String combinedName = arrayFieldName + "." + objectIdFieldName;
        return discoverTargetCollection(combinedName, sampleIds);
    }
    
    /**
     * Normalize field name for pattern detection
     */
    private String normalizeFieldName(String fieldName) {
        // Remove common suffixes and prefixes to find patterns
        return fieldName.toLowerCase()
            .replaceAll("(_id|id|_?ref|reference)$", "")
            .replaceAll("^(ref_|reference_)", "")
            .replaceAll("[_\\s]+", "");
    }
    
    /**
     * Get discovered relationships for saving to config
     */
    public Map<String, String> getDiscoveredRelationships() {
        return new HashMap<>(discoveredRelationships);
    }
    
    /**
     * Load previously discovered relationships (from config file)
     */
    public void loadDiscoveredRelationships(Map<String, String> relationships) {
        if (relationships != null) {
            discoveredRelationships.putAll(relationships);
            logger.info("Loaded {} previously discovered relationships", relationships.size());
        }
    }
    
    /**
     * Suggest potential relationships based on field name patterns
     * This is ONLY used as a last resort when we can't find actual data matches
     */
    public String suggestBasedOnPattern(String fieldName) {
        String normalized = normalizeFieldName(fieldName);
        
        // Check if we've seen this pattern before
        if (fieldToCollectionsMap.containsKey(normalized)) {
            Set<String> knownCollections = fieldToCollectionsMap.get(normalized);
            if (knownCollections.size() == 1) {
                String suggestion = knownCollections.iterator().next();
                logger.debug("Suggesting '{}' for field '{}' based on previous pattern", 
                           suggestion, fieldName);
                return suggestion;
            }
        }
        
        // No pattern found - return null (no guessing!)
        return null;
    }
}