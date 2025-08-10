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
public class RelationExpander {
    private static final Logger logger = LoggerFactory.getLogger(RelationExpander.class);
    
    private final MongoDatabase database;
    private final Map<String, RelationConfig> relationConfigs = new HashMap<>();
    
    // Cache for expanded documents
    private final Map<String, Map<ObjectId, Document>> cacheByCollection = new HashMap<>();
    
    public RelationExpander(MongoDatabase database) {
        this.database = database;
        initializeRelationConfigs();
    }
    
    /**
     * Define the relationship configurations for each collection
     */
    private void initializeRelationConfigs() {
        // Listings relations
        RelationConfig listingsConfig = new RelationConfig("listings");
        listingsConfig.addRelation("property", "properties", RelationType.MANY_TO_ONE);
        listingsConfig.addRelation("currentAgentId", "currentAgents", RelationType.MANY_TO_ONE);
        listingsConfig.addRelation("listingAgentId", "agents", RelationType.MANY_TO_ONE);
        listingsConfig.addRelation("_id", "transactions", RelationType.ONE_TO_MANY, "listing");
        listingsConfig.addRelation("_id", "listingSearch", RelationType.ONE_TO_ONE, "listingId");
        relationConfigs.put("listings", listingsConfig);
        
        // Transactions relations
        RelationConfig transactionsConfig = new RelationConfig("transactions");
        transactionsConfig.addRelation("listing", "listings", RelationType.MANY_TO_ONE);
        transactionsConfig.addRelation("property", "properties", RelationType.MANY_TO_ONE);
        transactionsConfig.addRelation("buyerAgent", "agents", RelationType.MANY_TO_ONE);
        transactionsConfig.addRelation("sellingAgentId", "agents", RelationType.MANY_TO_ONE);
        transactionsConfig.addRelation("buyers", "people", RelationType.MANY_TO_MANY);
        transactionsConfig.addRelation("sellers", "people", RelationType.MANY_TO_MANY);
        transactionsConfig.addRelation("_id", "transactionsderived", RelationType.ONE_TO_ONE, "_id");
        relationConfigs.put("transactions", transactionsConfig);
        
        // Agents relations
        RelationConfig agentsConfig = new RelationConfig("agents");
        agentsConfig.addRelation("person", "people", RelationType.MANY_TO_ONE);
        agentsConfig.addNestedRelation("realmData.brokerages", "brokerages", RelationType.MANY_TO_ONE);
        agentsConfig.addRelation("_id", "agentSearch", RelationType.ONE_TO_ONE, "agentId");
        agentsConfig.addRelation("_id", "agentclientevents", RelationType.ONE_TO_MANY, "agent");
        agentsConfig.addRelation("_id", "awards", RelationType.ONE_TO_MANY, "agents");
        agentsConfig.addRelation("_id", "teams", RelationType.ONE_TO_MANY, "agents");
        relationConfigs.put("agents", agentsConfig);
        
        // CurrentAgents relations (similar to agents)
        RelationConfig currentAgentsConfig = new RelationConfig("currentAgents");
        currentAgentsConfig.addRelation("person", "people", RelationType.MANY_TO_ONE);
        currentAgentsConfig.addNestedRelation("realmData.brokerages", "brokerages", RelationType.MANY_TO_ONE);
        relationConfigs.put("currentAgents", currentAgentsConfig);
        
        // People relations
        RelationConfig peopleConfig = new RelationConfig("people");
        peopleConfig.addRelation("_id", "residences", RelationType.ONE_TO_MANY, "person");
        peopleConfig.addRelation("_id", "agentclients", RelationType.ONE_TO_MANY, "person");
        relationConfigs.put("people", peopleConfig);
    }
    
    /**
     * Preload collections that should be cached in memory
     */
    public void preloadCollections(Set<String> collectionNames) {
        logger.info("Preloading {} collections for relation expansion...", collectionNames.size());
        
        for (String collectionName : collectionNames) {
            if (!cacheByCollection.containsKey(collectionName)) {
                long startTime = System.currentTimeMillis();
                Map<ObjectId, Document> cache = new HashMap<>();
                MongoCollection<Document> collection = database.getCollection(collectionName);
                
                // Get estimated count for progress
                long estimatedCount = collection.estimatedDocumentCount();
                logger.info("Preloading collection: {} (~{} docs)", collectionName, estimatedCount);
                
                long count = 0;
                for (Document doc : collection.find()) {
                    ObjectId id = doc.getObjectId("_id");
                    if (id != null) {
                        cache.put(id, doc);
                        count++;
                        
                        // More frequent updates for large collections
                        int interval = estimatedCount > 100000 ? 10000 : 5000;
                        if (count % interval == 0) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            double rate = count / (elapsed / 1000.0);
                            long remaining = estimatedCount - count;
                            int secondsLeft = remaining > 0 ? (int)(remaining / rate) : 0;
                            
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
            } else {
                logger.info("Collection {} already cached", collectionName);
            }
        }
        
        logger.info("All collections preloaded. Total cached documents: {}", 
            cacheByCollection.values().stream().mapToInt(Map::size).sum());
    }
    
    /**
     * Expand all relations for a document
     */
    public Document expandDocument(Document doc, String collectionName, int maxDepth) {
        return expandDocument(doc, collectionName, maxDepth, 0, new HashSet<>());
    }
    
    private Document expandDocument(Document doc, String collectionName, int maxDepth, int currentDepth, Set<String> visited) {
        if (doc == null || currentDepth >= maxDepth) {
            return doc;
        }
        
        String visitKey = collectionName + ":" + doc.getObjectId("_id");
        if (visited.contains(visitKey)) {
            return doc; // Prevent circular references
        }
        visited.add(visitKey);
        
        Document expanded = new Document(doc);
        RelationConfig config = relationConfigs.get(collectionName);
        
        if (config != null) {
            for (Relation relation : config.getRelations()) {
                expandRelation(expanded, relation, currentDepth, maxDepth, visited);
            }
        }
        
        return expanded;
    }
    
    private void expandRelation(Document doc, Relation relation, int currentDepth, int maxDepth, Set<String> visited) {
        Object value = getNestedValue(doc, relation.localField);
        if (value == null) {
            return;
        }
        
        String expandedFieldName = relation.localField + "_expanded";
        
        switch (relation.type) {
            case MANY_TO_ONE:
                if (value instanceof ObjectId) {
                    Document related = fetchDocument(relation.foreignCollection, (ObjectId) value);
                    if (related != null) {
                        doc.append(expandedFieldName, expandDocument(related, relation.foreignCollection, maxDepth, currentDepth + 1, visited));
                    }
                } else if (value instanceof Document && ((Document) value).containsKey("_id")) {
                    // Handle embedded documents with _id
                    ObjectId id = ((Document) value).getObjectId("_id");
                    if (id != null) {
                        Document related = fetchDocument(relation.foreignCollection, id);
                        if (related != null) {
                            doc.append(expandedFieldName, expandDocument(related, relation.foreignCollection, maxDepth, currentDepth + 1, visited));
                        }
                    }
                }
                break;
                
            case ONE_TO_ONE:
                if (value instanceof ObjectId) {
                    Document related = fetchDocumentByField(relation.foreignCollection, relation.foreignField, value);
                    if (related != null) {
                        doc.append(expandedFieldName, expandDocument(related, relation.foreignCollection, maxDepth, currentDepth + 1, visited));
                    }
                }
                break;
                
            case ONE_TO_MANY:
                if (value instanceof ObjectId) {
                    List<Document> related = fetchDocumentsByField(relation.foreignCollection, relation.foreignField, value);
                    if (!related.isEmpty()) {
                        List<Document> expandedList = related.stream()
                            .map(d -> expandDocument(d, relation.foreignCollection, maxDepth, currentDepth + 1, visited))
                            .collect(Collectors.toList());
                        doc.append(expandedFieldName, expandedList);
                    }
                }
                break;
                
            case MANY_TO_MANY:
                if (value instanceof List) {
                    List<Document> expandedList = new ArrayList<>();
                    for (Object item : (List<?>) value) {
                        if (item instanceof ObjectId) {
                            Document related = fetchDocument(relation.foreignCollection, (ObjectId) item);
                            if (related != null) {
                                expandedList.add(expandDocument(related, relation.foreignCollection, maxDepth, currentDepth + 1, visited));
                            }
                        }
                    }
                    if (!expandedList.isEmpty()) {
                        doc.append(expandedFieldName, expandedList);
                    }
                }
                break;
        }
    }
    
    private Document fetchDocument(String collectionName, ObjectId id) {
        // Check cache first
        Map<ObjectId, Document> cache = cacheByCollection.get(collectionName);
        if (cache != null) {
            return cache.get(id);
        }
        
        // Fetch from database
        MongoCollection<Document> collection = database.getCollection(collectionName);
        return collection.find(new Document("_id", id)).first();
    }
    
    private Document fetchDocumentByField(String collectionName, String fieldName, Object value) {
        // Check cache if field is _id
        if ("_id".equals(fieldName) && value instanceof ObjectId) {
            Map<ObjectId, Document> cache = cacheByCollection.get(collectionName);
            if (cache != null) {
                return cache.get(value);
            }
        }
        
        // Fetch from database
        MongoCollection<Document> collection = database.getCollection(collectionName);
        return collection.find(new Document(fieldName, value)).first();
    }
    
    private List<Document> fetchDocumentsByField(String collectionName, String fieldName, Object value) {
        List<Document> results = new ArrayList<>();
        
        // If cached, search through cache
        Map<ObjectId, Document> cache = cacheByCollection.get(collectionName);
        if (cache != null) {
            for (Document doc : cache.values()) {
                Object fieldValue = doc.get(fieldName);
                if (value.equals(fieldValue) || (fieldValue instanceof List && ((List<?>) fieldValue).contains(value))) {
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
    
    private Object getNestedValue(Document doc, String path) {
        String[] parts = path.split("\\.");
        Object current = doc;
        
        for (String part : parts) {
            if (current instanceof Document) {
                current = ((Document) current).get(part);
            } else if (current instanceof List && !((List<?>) current).isEmpty()) {
                // Handle array access (take first element)
                current = ((List<?>) current).get(0);
                if (current instanceof Document) {
                    current = ((Document) current).get(part);
                }
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    /**
     * Get collections that should be preloaded for a given collection type
     */
    public Set<String> getPreloadCollections(String collectionName) {
        Set<String> toPreload = new HashSet<>();
        RelationConfig config = relationConfigs.get(collectionName);
        
        if (config != null) {
            for (Relation relation : config.getRelations()) {
                // Preload small collections and frequently accessed ones
                if (isSmallCollection(relation.foreignCollection)) {
                    toPreload.add(relation.foreignCollection);
                }
            }
        }
        
        return toPreload;
    }
    
    private boolean isSmallCollection(String collectionName) {
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
    
    private static class RelationConfig {
        private final String collectionName;
        private final List<Relation> relations = new ArrayList<>();
        
        public RelationConfig(String collectionName) {
            this.collectionName = collectionName;
        }
        
        public void addRelation(String localField, String foreignCollection, RelationType type) {
            relations.add(new Relation(localField, foreignCollection, type, "_id"));
        }
        
        public void addRelation(String localField, String foreignCollection, RelationType type, String foreignField) {
            relations.add(new Relation(localField, foreignCollection, type, foreignField));
        }
        
        public void addNestedRelation(String localField, String foreignCollection, RelationType type) {
            relations.add(new Relation(localField, foreignCollection, type, "_id"));
        }
        
        public List<Relation> getRelations() {
            return relations;
        }
    }
    
    private static class Relation {
        final String localField;
        final String foreignCollection;
        final RelationType type;
        final String foreignField;
        
        public Relation(String localField, String foreignCollection, RelationType type, String foreignField) {
            this.localField = localField;
            this.foreignCollection = foreignCollection;
            this.type = type;
            this.foreignField = foreignField;
        }
    }
    
    private enum RelationType {
        MANY_TO_ONE,   // Foreign key in local document
        ONE_TO_ONE,    // Unique foreign key
        ONE_TO_MANY,   // Foreign key in foreign document pointing back
        MANY_TO_MANY   // Array of foreign keys in local document
    }
}