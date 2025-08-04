package com.example.mongoexport;

import com.mongodb.client.*;
import com.opencsv.CSVWriter;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ultra-comprehensive listings exporter with all property, agent, and brokerage details
 */
public class UltraListingsExporter {
    private static final Logger logger = LoggerFactory.getLogger(UltraListingsExporter.class);
    private static final int BATCH_SIZE = 2000; // Process 2000 listings at a time
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    
    private final ExportConfig config;
    private final MongoDatabase database;
    
    // In-memory lookup maps
    private Map<ObjectId, Document> brokeragesMap = new HashMap<>();
    private Map<ObjectId, Document> currentAgentsMap = new HashMap<>();
    private Map<ObjectId, Document> peopleMap = new HashMap<>();
    private Map<ObjectId, List<Document>> transactionsByListingMap = new HashMap<>();
    
    // Additional collections for enhanced attributes
    private Map<ObjectId, Document> marketProfilesMap = new HashMap<>();
    private Map<String, Document> usCitiesMap = new HashMap<>(); // Key by city+state
    private Map<ObjectId, Document> listingSearchMap = new HashMap<>();
    private Map<ObjectId, Document> tagsMap = new HashMap<>();
    private Map<String, List<Document>> feederMarketsMap = new HashMap<>(); // Key by city+state
    
    // Common tag values found in the database
    private static final List<String> COMMON_TAGS = Arrays.asList("featured", "coming_soon", "price_reduced", "open_house", "virtual_tour", "new_construction", "foreclosure", "short_sale", "auction", "bank_owned", "reo", "distressed", "fixer_upper", "as_is", "estate_sale", "probate", "relocation", "corporate_owned", "government_owned", "tax_lien", "pre_foreclosure", "hud_home", "fannie_mae", "freddie_mac", "va_owned");
    
    // Column statistics tracking
    private Map<Integer, Map<String, Integer>> columnStats = new HashMap<>();
    private Map<Integer, Integer> nullCounts = new HashMap<>();
    private int totalRows = 0;
    
    public UltraListingsExporter() {
        this.config = new ExportConfig();
        MongoClient mongoClient = MongoClients.create(config.getMongoUrl());
        this.database = mongoClient.getDatabase(config.getDatabaseName());
    }
    
    public static void main(String[] args) {
        UltraListingsExporter exporter = new UltraListingsExporter();
        
        logger.info("Starting ultra-comprehensive listings export...");
        
        // Count listings first
        MongoCollection<Document> listings = exporter.database.getCollection("listings");
        long totalListings = listings.countDocuments();
        logger.info("Total listings in database: {}", totalListings);
        
        exporter.exportAllListingsUltraComprehensive();
        logger.info("Ultra-comprehensive listings export completed!");
    }
    
    private void loadCollectionsIntoMemory() {
        logger.info("Loading collections into memory for fast lookups (with 20GB available)...");
        
        // Load currentAgents
        logger.info("Loading currentAgents collection...");
        MongoCollection<Document> currentAgents = database.getCollection("currentAgents");
        for (Document doc : currentAgents.find()) {
            ObjectId id = doc.getObjectId("_id");
            if (id != null) {
                currentAgentsMap.put(id, doc);
            }
        }
        logger.info("Loaded {} currentAgents into memory", currentAgentsMap.size());
        
        // Load brokerages (small collection)
        logger.info("Loading brokerages collection...");
        MongoCollection<Document> brokerages = database.getCollection("brokerages");
        for (Document doc : brokerages.find()) {
            ObjectId id = doc.getObjectId("_id");
            if (id != null) {
                brokeragesMap.put(id, doc);
            }
        }
        logger.info("Loaded {} brokerages into memory", brokeragesMap.size());
        
        // Load people (620K docs - should fit in memory with 20GB)
        logger.info("Loading people collection...");
        MongoCollection<Document> people = database.getCollection("people");
        long peopleCount = 0;
        for (Document doc : people.find()) {
            ObjectId id = doc.getObjectId("_id");
            if (id != null) {
                peopleMap.put(id, doc);
                peopleCount++;
                if (peopleCount % 100000 == 0) {
                    logger.info("  Loaded {} people...", peopleCount);
                }
            }
        }
        logger.info("Loaded {} people into memory", peopleMap.size());
        
        // Load transactions grouped by listing (23K docs)
        logger.info("Loading transactions collection...");
        MongoCollection<Document> transactions = database.getCollection("transactions");
        for (Document doc : transactions.find()) {
            Object listingRef = doc.get("listing");
            if (listingRef instanceof ObjectId) {
                ObjectId listingId = (ObjectId) listingRef;
                transactionsByListingMap.computeIfAbsent(listingId, k -> new ArrayList<>()).add(doc);
            }
        }
        logger.info("Loaded {} transactions into memory", transactionsByListingMap.values().stream().mapToInt(List::size).sum());
        
        // Load market profiles (12K docs)
        logger.info("Loading market profiles collection...");
        MongoCollection<Document> marketProfiles = database.getCollection("marketProfiles");
        for (Document doc : marketProfiles.find()) {
            ObjectId id = doc.getObjectId("_id");
            if (id != null) {
                marketProfilesMap.put(id, doc);
            }
        }
        logger.info("Loaded {} market profiles into memory", marketProfilesMap.size());
        
        // Load US cities (7.8K docs)
        logger.info("Loading US cities collection...");
        MongoCollection<Document> usCities = database.getCollection("usCities");
        for (Document doc : usCities.find()) {
            String city = doc.getString("city");
            String state = doc.getString("state");
            if (city != null && state != null) {
                String key = city.toLowerCase() + "|" + state.toLowerCase();
                usCitiesMap.put(key, doc);
            }
        }
        logger.info("Loaded {} US cities into memory", usCitiesMap.size());
        
        // Load listing search data (12K docs)
        logger.info("Loading listing search collection...");
        MongoCollection<Document> listingSearch = database.getCollection("listingSearch");
        for (Document doc : listingSearch.find()) {
            Object listingIdObj = doc.get("listingId");
            if (listingIdObj instanceof ObjectId) {
                listingSearchMap.put((ObjectId) listingIdObj, doc);
            }
        }
        logger.info("Loaded {} listing search records into memory", listingSearchMap.size());
        
        // Load tags (600 docs)
        logger.info("Loading tags collection...");
        MongoCollection<Document> tags = database.getCollection("tags");
        for (Document doc : tags.find()) {
            ObjectId id = doc.getObjectId("_id");
            if (id != null) {
                tagsMap.put(id, doc);
            }
        }
        logger.info("Loaded {} tags into memory", tagsMap.size());
        
        // Report memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        logger.info("Memory usage after loading collections: {} MB", usedMemory);
    }
    
    public void exportAllListingsUltraComprehensive() {
        logger.info("Exporting ALL listings with ultra-comprehensive fields...");
        
        // Load collections into memory
        loadCollectionsIntoMemory();
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String outputPath = config.getOutputDirectory() + "/all_listings_ultra_comprehensive_" + timestamp + ".csv";
        String statsPath = config.getOutputDirectory() + "/all_listings_ultra_comprehensive_" + timestamp + "_stats.txt";
        
        try (FileWriter fileWriter = new FileWriter(outputPath); 
             CSVWriter csvWriter = new CSVWriter(fileWriter)) {
            
            // Write headers
            String[] headers = buildComprehensiveHeaders();
            csvWriter.writeNext(headers);
            
            // Initialize column statistics
            for (int i = 0; i < headers.length; i++) {
                columnStats.put(i, new HashMap<>());
                nullCounts.put(i, 0);
            }
            
            MongoCollection<Document> listings = database.getCollection("listings");
            MongoCollection<Document> properties = database.getCollection("properties");
            MongoCollection<Document> currentAgents = database.getCollection("currentAgents");
            MongoCollection<Document> people = database.getCollection("people");
            MongoCollection<Document> transactions = database.getCollection("transactions");
            
            AtomicInteger totalCount = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();
            
            // Process listings in batches
            List<Document> batch = new ArrayList<>();
            
            try (MongoCursor<Document> cursor = listings.find().iterator()) {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    
                    if (batch.size() >= BATCH_SIZE || !cursor.hasNext()) {
                        // Collect all IDs needed for this batch
                        Set<ObjectId> propertyIds = new HashSet<>();
                        Set<ObjectId> currentAgentIds = new HashSet<>();
                        Set<ObjectId> listingIds = new HashSet<>();
                        
                        for (Document listing : batch) {
                            Object propertyRef = listing.get("property");
                            if (propertyRef instanceof ObjectId) {
                                propertyIds.add((ObjectId) propertyRef);
                            }
                            Object currentAgentRef = listing.get("currentAgentId");
                            if (currentAgentRef instanceof ObjectId) {
                                currentAgentIds.add((ObjectId) currentAgentRef);
                            }
                            Object listingId = listing.get("_id");
                            if (listingId instanceof ObjectId) {
                                listingIds.add((ObjectId) listingId);
                            }
                        }
                        
                        // Batch fetch properties
                        Map<ObjectId, Document> batchProperties = new HashMap<>();
                        if (!propertyIds.isEmpty()) {
                            properties.find(new Document("_id", new Document("$in", new ArrayList<>(propertyIds))))
                                    .forEach(doc -> {
                                        ObjectId id = doc.getObjectId("_id");
                                        if (id != null) batchProperties.put(id, doc);
                                    });
                        }
                        
                        // Batch fetch currentAgents and related people (if not in memory)
                        Map<ObjectId, Document> batchAgents = new HashMap<>();
                        Map<ObjectId, Document> batchPeople = new HashMap<>();
                        if (!currentAgentIds.isEmpty()) {
                            // Check memory first
                            currentAgents.find(new Document("_id", new Document("$in", new ArrayList<>(currentAgentIds))))
                                    .forEach(doc -> {
                                        ObjectId id = doc.getObjectId("_id");
                                        if (id != null) batchAgents.put(id, doc);
                                    });
                            
                            // Collect person IDs from currentAgents
                            Set<ObjectId> personIds = new HashSet<>();
                            for (Document agent : batchAgents.values()) {
                                Object personRef = agent.get("person");
                                if (personRef instanceof ObjectId) {
                                    personIds.add((ObjectId) personRef);
                                }
                            }
                            
                            // Batch fetch people
                            if (!personIds.isEmpty()) {
                                people.find(new Document("_id", new Document("$in", new ArrayList<>(personIds))))
                                        .forEach(doc -> {
                                            ObjectId id = doc.getObjectId("_id");
                                            if (id != null) batchPeople.put(id, doc);
                                        });
                            }
                        }
                        
                        // Batch fetch transactions
                        Map<ObjectId, Document> batchTransactions = new HashMap<>();
                        if (!listingIds.isEmpty()) {
                            transactions.find(new Document("listing", new Document("$in", new ArrayList<>(listingIds))))
                                    .forEach(doc -> {
                                        Object listingRef = doc.get("listing");
                                        if (listingRef instanceof ObjectId) {
                                            batchTransactions.put((ObjectId) listingRef, doc);
                                        }
                                    });
                        }
                        
                        // Process batch
                        for (Document listing : batch) {
                            ObjectId listingId = listing.getObjectId("_id");
                            
                            // Get property from batch or memory
                            Document property = null;
                            Object propertyRef = listing.get("property");
                            if (propertyRef instanceof ObjectId) {
                                property = batchProperties.get((ObjectId) propertyRef);
                            }
                            
                            // Get currentAgent from batch or memory
                            Document currentAgent = null;
                            Object currentAgentRef = listing.get("currentAgentId");
                            if (currentAgentRef instanceof ObjectId) {
                                currentAgent = currentAgentsMap.getOrDefault((ObjectId) currentAgentRef, 
                                                                             batchAgents.get((ObjectId) currentAgentRef));
                            }
                            
                            // Get person if currentAgent exists
                            Document person = null;
                            if (currentAgent != null) {
                                Object personRef = currentAgent.get("person");
                                if (personRef instanceof ObjectId) {
                                    person = peopleMap.getOrDefault((ObjectId) personRef,
                                                                    batchPeople.get((ObjectId) personRef));
                                }
                            }
                            
                            // Build row with all data
                            String[] row = buildComprehensiveRow(listing, property, currentAgent, person);
                            csvWriter.writeNext(row);
                            
                            // Collect statistics for this row
                            collectRowStatistics(row);
                            
                            totalCount.incrementAndGet();
                            totalRows++;
                        }
                        
                        // Log progress
                        int count = totalCount.get();
                        if (count % 5000 == 0) {
                            long currentTime = System.currentTimeMillis();
                            double secondsElapsed = (currentTime - startTime) / 1000.0;
                            double rate = count / secondsElapsed;
                            logger.info("Processed {} listings... ({} listings/sec)", 
                                count, String.format("%.1f", rate));
                        }
                        
                        // Clear batch
                        batch.clear();
                    }
                }
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            double totalSeconds = totalTime / 1000.0;
            logger.info("Export completed: {} total listings written to {}", totalCount.get(), outputPath);
            logger.info("Total time: {} seconds ({} listings/sec)", 
                String.format("%.1f", totalSeconds), String.format("%.1f", totalCount.get() / totalSeconds));
            
            // Write statistics file
            writeStatisticsFile(statsPath, headers);
            
        } catch (IOException e) {
            logger.error("Failed to export listings", e);
        }
    }
    
    private String[] buildComprehensiveHeaders() {
        List<String> headers = new ArrayList<>();
        
        // Core listing fields
        headers.addAll(Arrays.asList("Listing ID", "MLS Number", "Status", "Status Category", 
            "List Date", "Days on Market", "Expiration Date", "Last Update"));
            
        // Price information
        headers.addAll(Arrays.asList("List Price", "Price per Sq Ft", "Price History Count", 
            "Original List Price", "Current Price Index", "HOA Fee", "Listing Type", "Listing Agreement", 
            "Commission Percent", "Showing Requirements"));
            
        // Property basic info
        headers.addAll(Arrays.asList("Property ID", "Property Type", "Property Sub-Type", 
            "Year Built", "Square Feet", "Lot Size", "Bedrooms", "Full Bathrooms", "Half Bathrooms", 
            "Total Bathrooms", "Rooms", "Stories", "Garage Spaces", "Carport Spaces", "Parking Spaces"));
            
        // No property features section - removed all 17 always-false fields
        
        // No extended features section - removed ~25 always-false fields
        
        // Address and location
        headers.addAll(Arrays.asList("Street Address", "Unit Number", "City", "State", "ZIP Code", "County", 
            "Subdivision", "Latitude", "Longitude", "Geo Precision", "Neighborhood", "Directions"));
            
        // School information
        headers.addAll(Arrays.asList("Elementary School", "Elementary Rating", "Elementary District", 
            "Elementary Distance (mi)", "Middle School", "Middle Rating", "Middle District", "Middle Distance (mi)", 
            "High School", "High Rating", "High District", "High Distance (mi)"));
            
        // No lifestyle indicators section - removed ~25 always-false fields
        
        // Tag indicators - only meaningful ones
        for (String tag : COMMON_TAGS) {
            String formattedTag = tag.replace("_", " ");
            formattedTag = formattedTag.substring(0, 1).toUpperCase() + formattedTag.substring(1).toLowerCase();
            headers.add("Tag: " + formattedTag);
        }
        
        // Current agent information
        headers.addAll(Arrays.asList("Agent ID", "Agent Name", "Agent Email", 
            "Agent Phone", "Agent License #", "Agent Website", "Agent Bio Length", 
            "Agent Photo URL", "Agent Years Experience", "Agent Designations", 
            "Agent Specialties", "Agent Languages", "Agent Education", 
            "Agent Social Media", "Agent Rating (Avg)", "Agent Rating Count", 
            "Agent Sales Last Year", "Agent Listings Last Year"));
            
        // Current agent person info
        headers.addAll(Arrays.asList("Agent Person ID", "Agent Address", "Agent City", "Agent State", 
            "Agent ZIP Code", "Agent Person Languages", "Agent Person Specialties"));
            
        // Brokerage information - with fixed city extraction
        headers.addAll(Arrays.asList("Brokerage ID", "Brokerage Name", "Brokerage Phone", "Brokerage Email", 
            "Brokerage Website", "Brokerage Address", "Brokerage City", "Brokerage State", 
            "Brokerage ZIP Code", "Brokerage Type", "Brokerage Year Established"));
            
        // Marketing and media
        headers.addAll(Arrays.asList("Picture Count", "Virtual Tour URL", "Video URL", 
            "Walkthrough Video URL", "Drone Video URL", "Matterport URL", "Marketing Remarks", 
            "Private Remarks", "Property Description"));
            
        // Showing information
        headers.addAll(Arrays.asList("Showing Instructions", "Showing Contact", "Lockbox Type", 
            "Lockbox Code", "Gate Code", "Alarm Info"));
            
        // Transaction history
        headers.addAll(Arrays.asList("Has Transaction", "Sale Date", "Sale Price", "Sale to List Ratio (%)", 
            "Days to Sell", "Buyer Agent ID", "Buyer Agent Name", "Buyer Brokerage", "Buyers Count", 
            "Buyer Names", "Sellers Count", "Seller Names", "Financing Type"));
            
        // Market Profile Data
        headers.addAll(Arrays.asList("Market Area", "Market Median List Price", "Market Median Sale Price",
            "Market Avg Days on Market", "Market Inventory Count", "Market New Listings (30d)", 
            "Market Sold Listings (30d)", "Market Price Trend (3m)", "Market Price Trend (12m)",
            "Market Absorption Rate", "Market Months of Inventory"));
            
        // Demographics
        headers.addAll(Arrays.asList("Market Population", "Market Households", "Market Median Income",
            "Market Age Distribution", "Market Income Distribution", "Market Marital Distribution",
            "Market Education Level", "Market Employment Rate", "Market Owner-Occupied Rate"));
            
        // US Cities Data
        headers.addAll(Arrays.asList("City Population", "City Median Income", "City Median Home Value",
            "City Unemployment Rate", "City Cost of Living Index", "City Crime Rate", 
            "City School Rating", "City Walk Score", "City Transit Score"));
            
        // Enhanced Tags and Categories
        headers.addAll(Arrays.asList("Listing Lifestyles", "Listing Tags", 
            "Co-Ownership Type", "Tag Categories", "Tag Weights Sum"));
            
        // Feeder Markets
        headers.addAll(Arrays.asList("Feeder Market Count", "Primary Feeder Market", 
            "Secondary Feeder Markets", "Feeder Market Distances"));
        
        return headers.toArray(new String[0]);
    }
    
    private String[] buildComprehensiveRow(Document listing, Document property, Document currentAgent, Document person) {
        List<String> row = new ArrayList<>();
        
        // Core listing fields
        row.add(safeGetString(listing, "_id"));
        row.add(safeGetString(listing, "mlsNumber"));
        row.add(safeGetString(listing, "status"));
        row.add(categorizeStatus(safeGetString(listing, "status")));
        
        // Dates and timing
        Date listDate = (Date) listing.get("dateListed");
        if (listDate != null) {
            row.add(DATE_FORMAT.format(listDate));
            long daysOnMarket = (System.currentTimeMillis() - listDate.getTime()) / (1000 * 60 * 60 * 24);
            row.add(String.valueOf(daysOnMarket));
        } else {
            row.add("");
            row.add("");
        }
        // Format expiration date if it exists
        Date expirationDate = (Date) listing.get("expirationDate");
        row.add(expirationDate != null ? DATE_FORMAT.format(expirationDate) : "");
        
        // Format last update date if it exists
        Date lastUpdate = (Date) listing.get("lastUpdate");
        row.add(lastUpdate != null ? DATE_FORMAT.format(lastUpdate) : "");
        
        // Price information
        Double listPrice = null;
        Object priceObj = listing.get("price");
        if (priceObj instanceof Document) {
            listPrice = safeGetDouble((Document) priceObj, "amount");
        }
        row.add(listPrice != null ? String.format("%.2f", listPrice) : "");
        
        // Price per sqft
        if (listPrice != null && property != null) {
            Double sqft = safeGetDouble(property, "squareFeet");
            if (sqft != null && sqft > 0) {
                row.add(String.format("%.2f", listPrice / sqft));
            } else {
                row.add("");
            }
        } else {
            row.add("");
        }
        
        // Price history
        Object priceHistoryObj = listing.get("priceHistory");
        if (priceHistoryObj instanceof List) {
            row.add(String.valueOf(((List<?>) priceHistoryObj).size()));
        } else {
            row.add("0");
        }
        
        row.add(safeGetString(listing, "originalListPrice"));
        row.add(safeGetString(listing, "currentPriceIndex"));
        row.add(safeGetString(listing, "hoaFee"));
        row.add(safeGetString(listing, "listingType"));
        row.add(safeGetString(listing, "listingAgreement"));
        row.add(safeGetString(listing, "commissionPercent"));
        row.add(safeGetString(listing, "showingRequirements"));
        
        // Property information
        if (property != null) {
            row.add(safeGetString(property, "_id"));
            row.add(safeGetString(property, "propertyType"));
            row.add(safeGetString(property, "propertySubType"));
            row.add(safeGetString(property, "yearBuilt"));
            row.add(safeGetString(property, "squareFeet"));
            row.add(safeGetString(property, "lotSize"));
            row.add(safeGetString(property, "bedrooms"));
            row.add(safeGetString(property, "fullBathrooms"));
            row.add(safeGetString(property, "halfBathrooms"));
            row.add(safeGetString(property, "totalBathrooms"));
            row.add(safeGetString(property, "rooms"));
            row.add(safeGetString(property, "stories"));
            row.add(safeGetString(property, "garageSpaces"));
            row.add(safeGetString(property, "carportSpaces"));
            row.add(safeGetString(property, "parkingSpaces"));
            
            // Address information
            Document address = (Document) property.get("address");
            if (address != null) {
                row.add(safeGetString(address, "streetAddress"));
                row.add(safeGetString(address, "unitNumber"));
                row.add(safeGetString(address, "city"));
                row.add(safeGetString(address, "state"));
                row.add(safeGetString(address, "zip"));
                row.add(safeGetString(address, "county"));
                row.add(safeGetString(address, "subdivision"));
                
                Document location = (Document) address.get("location");
                if (location != null) {
                    row.add(safeGetString(location, "latitude"));
                    row.add(safeGetString(location, "longitude"));
                    row.add(safeGetString(location, "precision"));
                } else {
                    row.add("");
                    row.add("");
                    row.add("");
                }
                
                row.add(safeGetString(address, "neighborhood"));
                row.add(safeGetString(address, "directions"));
            } else {
                for (int i = 0; i < 12; i++) row.add("");
            }
            
            // School information
            Object schoolsObj = property.get("schools");
            if (schoolsObj instanceof List && !((List<?>) schoolsObj).isEmpty()) {
                List<Document> schools = (List<Document>) schoolsObj;
                // Elementary school
                Document elementary = schools.stream()
                    .filter(s -> "elementary".equalsIgnoreCase(safeGetString(s, "level")))
                    .findFirst().orElse(null);
                if (elementary != null) {
                    row.add(safeGetString(elementary, "name"));
                    row.add(safeGetString(elementary, "rating"));
                    row.add(safeGetString(elementary, "district"));
                    row.add(safeGetString(elementary, "distanceInMiles"));
                } else {
                    for (int i = 0; i < 4; i++) row.add("");
                }
                
                // Middle school
                Document middle = schools.stream()
                    .filter(s -> "middle".equalsIgnoreCase(safeGetString(s, "level")))
                    .findFirst().orElse(null);
                if (middle != null) {
                    row.add(safeGetString(middle, "name"));
                    row.add(safeGetString(middle, "rating"));
                    row.add(safeGetString(middle, "district"));
                    row.add(safeGetString(middle, "distanceInMiles"));
                } else {
                    for (int i = 0; i < 4; i++) row.add("");
                }
                
                // High school
                Document high = schools.stream()
                    .filter(s -> "high".equalsIgnoreCase(safeGetString(s, "level")))
                    .findFirst().orElse(null);
                if (high != null) {
                    row.add(safeGetString(high, "name"));
                    row.add(safeGetString(high, "rating"));
                    row.add(safeGetString(high, "district"));
                    row.add(safeGetString(high, "distanceInMiles"));
                } else {
                    for (int i = 0; i < 4; i++) row.add("");
                }
            } else {
                for (int i = 0; i < 12; i++) row.add("");
            }
            
            // Tag indicators
            List<String> tags = safeGetStringList(property, "tags");
            for (String tag : COMMON_TAGS) {
                boolean hasTag = tags.stream()
                    .anyMatch(t -> t.toLowerCase().contains(tag));
                row.add(hasTag ? "true" : "false");
            }
        } else {
            // No property found - add empty fields
            for (int i = 0; i < 15; i++) row.add(""); // Property basic info
            for (int i = 0; i < 12; i++) row.add(""); // Address
            for (int i = 0; i < 12; i++) row.add(""); // Schools
            for (int i = 0; i < COMMON_TAGS.size(); i++) row.add("false"); // Tags
        }
        
        // Current agent information
        if (currentAgent != null) {
            row.add(safeGetString(currentAgent, "_id"));
            row.add(safeGetString(currentAgent, "name"));
            row.add(safeGetString(currentAgent, "email"));
            row.add(safeGetString(currentAgent, "phone"));
            row.add(safeGetString(currentAgent, "licenseNumber"));
            row.add(safeGetString(currentAgent, "website"));
            
            String bio = safeGetString(currentAgent, "bio");
            row.add(String.valueOf(bio.length()));
            row.add(safeGetString(currentAgent, "photoUrl"));
            
            // Calculate years of experience
            Date startDate = (Date) currentAgent.get("professionalStartDate");
            if (startDate != null) {
                long years = (System.currentTimeMillis() - startDate.getTime()) / (1000L * 60 * 60 * 24 * 365);
                row.add(String.valueOf(years));
            } else {
                row.add("");
            }
            
            row.add(safeGetString(currentAgent, "designations"));
            row.add(safeGetString(currentAgent, "specialties"));
            row.add(safeGetString(currentAgent, "languages"));
            row.add(safeGetString(currentAgent, "education"));
            row.add(safeGetString(currentAgent, "socialMedia"));
            
            Document ratings = (Document) currentAgent.get("ratings");
            if (ratings != null) {
                row.add(safeGetString(ratings, "average"));
                row.add(safeGetString(ratings, "count"));
            } else {
                row.add("");
                row.add("");
            }
            
            row.add(safeGetString(currentAgent, "salesLastYear"));
            row.add(safeGetString(currentAgent, "listingsLastYear"));
            
            // Agent person information
            if (person != null) {
                row.add(safeGetString(person, "_id"));
                
                Document personAddress = (Document) person.get("address");
                if (personAddress != null) {
                    row.add(safeGetString(personAddress, "fullAddress"));
                    row.add(safeGetString(personAddress, "city"));
                    row.add(safeGetString(personAddress, "state"));
                    row.add(safeGetString(personAddress, "zipcode"));
                } else {
                    for (int i = 0; i < 4; i++) row.add("");
                }
                
                List<String> personLanguages = safeGetStringList(person, "languages");
                row.add(String.join(",", personLanguages));
                
                List<String> personSpecialties = safeGetStringList(person, "specialties");
                row.add(String.join(",", personSpecialties));
            } else {
                for (int i = 0; i < 7; i++) row.add("");
            }
            
            // Brokerage information - with fixed city extraction
            Document realmData = (Document) currentAgent.get("realmData");
            if (realmData != null) {
                Object brokeragesObj = realmData.get("brokerages");
                if (brokeragesObj instanceof List) {
                    List<?> brokerageList = (List<?>) brokeragesObj;
                    if (!brokerageList.isEmpty()) {
                        Object firstBrokerage = brokerageList.get(0);
                        ObjectId brokerageId = null;
                        
                        if (firstBrokerage instanceof Document) {
                            Document brokerageInfo = (Document) firstBrokerage;
                            Object brokerageRef = brokerageInfo.get("_id");
                            if (brokerageRef instanceof ObjectId) {
                                brokerageId = (ObjectId) brokerageRef;
                            }
                        } else if (firstBrokerage instanceof ObjectId) {
                            brokerageId = (ObjectId) firstBrokerage;
                        }
                        
                        if (brokerageId != null) {
                            Document currentAgentBrokerage = brokeragesMap.get(brokerageId);
                            if (currentAgentBrokerage != null) {
                                row.add(safeGetString(currentAgentBrokerage, "_id"));
                                row.add(safeGetString(currentAgentBrokerage, "name"));
                                
                                // Extract office information from the offices array
                                String brokeragePhone = "";
                                String brokerageEmail = "";
                                String brokerageWebsite = "";
                                String brokerageAddress = "";
                                String brokerageCity = "";
                                String brokerageState = "";
                                String brokerageZipcode = "";
                                
                                // Get first office information if available
                                Object officesObj = currentAgentBrokerage.get("offices");
                                if (officesObj instanceof List && !((List<?>) officesObj).isEmpty()) {
                                    List<Document> offices = (List<Document>) officesObj;
                                    Document firstOffice = offices.get(0);
                                    brokeragePhone = safeGetString(firstOffice, "phone");
                                    brokerageEmail = safeGetString(firstOffice, "email");
                                    brokerageWebsite = safeGetString(firstOffice, "website");
                                    brokerageAddress = safeGetString(firstOffice, "address");
                                    brokerageCity = safeGetString(firstOffice, "city");
                                    brokerageState = safeGetString(firstOffice, "state");
                                    brokerageZipcode = safeGetString(firstOffice, "zipcode");
                                }
                                
                                // Also check realmData for additional information
                                Document brokerageRealmData = (Document) currentAgentBrokerage.get("realmData");
                                if (brokerageRealmData != null) {
                                    if (brokerageWebsite.isEmpty()) {
                                        brokerageWebsite = safeGetString(brokerageRealmData, "website");
                                    }
                                }
                                
                                row.add(brokeragePhone);
                                row.add(brokerageEmail);
                                row.add(brokerageWebsite);
                                row.add(brokerageAddress);
                                row.add(brokerageCity);
                                row.add(brokerageState);
                                row.add(brokerageZipcode);
                                row.add(safeGetString(currentAgentBrokerage, "type"));
                                row.add(safeGetString(currentAgentBrokerage, "yearEstablished"));
                            } else {
                                for (int i = 0; i < 11; i++) row.add("");
                            }
                        } else {
                            for (int i = 0; i < 11; i++) row.add("");
                        }
                    } else {
                        for (int i = 0; i < 11; i++) row.add("");
                    }
                } else {
                    for (int i = 0; i < 11; i++) row.add("");
                }
            } else {
                for (int i = 0; i < 11; i++) row.add("");
            }
        } else {
            // No currentAgent found - add empty fields for all currentAgent columns
            for (int i = 0; i < 29; i++) row.add("");
        }
        
        // Marketing and media
        Object picturesObj = listing.get("pictures");
        if (picturesObj instanceof List) {
            row.add(String.valueOf(((List<?>) picturesObj).size()));
        } else {
            row.add("0");
        }
        row.add(safeGetString(listing, "virtualTourUrl"));
        row.add(safeGetString(listing, "videoUrl"));
        row.add(safeGetString(listing, "walkthroughVideoUrl"));
        row.add(safeGetString(listing, "droneVideoUrl"));
        row.add(safeGetString(listing, "matterportUrl"));
        row.add(safeGetString(listing, "marketingRemarks"));
        row.add(safeGetString(listing, "privateRemarks"));
        
        Object descriptionObj = listing.get("description");
        if (descriptionObj instanceof Document) {
            Document description = (Document) descriptionObj;
            row.add(safeGetString(description, "en"));
        } else if (descriptionObj instanceof String) {
            row.add((String) descriptionObj);
        } else {
            row.add("");
        }
        
        // Showing information
        row.add(safeGetString(listing, "showingInstructions"));
        row.add(safeGetString(listing, "showingContact"));
        row.add(safeGetString(listing, "lockboxType"));
        row.add(safeGetString(listing, "lockboxCode"));
        row.add(safeGetString(listing, "gateCode"));
        row.add(safeGetString(listing, "alarmInfo"));
        
        // Transaction history
        ObjectId listingId = listing.getObjectId("_id");
        List<Document> listingTransactions = transactionsByListingMap.get(listingId);
        
        if (listingTransactions != null && !listingTransactions.isEmpty()) {
            row.add("true");
            Document transaction = listingTransactions.get(0); // Get most recent
            
            Date closingDate = (Date) transaction.get("closingDate");
            row.add(closingDate != null ? DATE_FORMAT.format(closingDate) : "");
            
            Double salePrice = safeGetDouble(transaction, "price");
            row.add(salePrice != null ? String.format("%.2f", salePrice) : "");
            
            if (salePrice != null && listPrice != null && listPrice > 0) {
                row.add(String.format("%.2f", (salePrice / listPrice) * 100));
            } else {
                row.add("");
            }
            
            Date saleDate = (Date) transaction.get("closingDate");
            if (saleDate != null && listDate != null) {
                long daysToSell = (saleDate.getTime() - listDate.getTime()) / (1000 * 60 * 60 * 24);
                row.add(String.valueOf(daysToSell));
            } else {
                row.add("");
            }
            
            row.add(safeGetString(transaction, "buyerAgentId"));
            row.add(safeGetString(transaction, "buyerAgentName"));
            row.add(safeGetString(transaction, "buyerBrokerage"));
            
            Object buyersObj = transaction.get("buyers");
            if (buyersObj instanceof List) {
                List<?> buyers = (List<?>) buyersObj;
                row.add(String.valueOf(buyers.size()));
                List<String> buyerNames = new ArrayList<>();
                for (Object buyer : buyers) {
                    if (buyer instanceof Document) {
                        buyerNames.add(safeGetString((Document) buyer, "name"));
                    }
                }
                row.add(String.join("; ", buyerNames));
            } else {
                row.add("0");
                row.add("");
            }
            
            Object sellersObj = transaction.get("sellers");
            if (sellersObj instanceof List) {
                List<?> sellers = (List<?>) sellersObj;
                row.add(String.valueOf(sellers.size()));
                List<String> sellerNames = new ArrayList<>();
                for (Object seller : sellers) {
                    if (seller instanceof Document) {
                        sellerNames.add(safeGetString((Document) seller, "name"));
                    }
                }
                row.add(String.join("; ", sellerNames));
            } else {
                row.add("0");
                row.add("");
            }
            
            row.add(safeGetString(transaction, "financingType"));
        } else {
            row.add("false");
            for (int i = 0; i < 12; i++) row.add("");
        }
        
        // Market Profile Data
        if (property != null) {
            Document address = (Document) property.get("address");
            if (address != null) {
                String city = safeGetString(address, "city");
                String state = safeGetString(address, "state");
                String marketKey = city.toLowerCase() + "|" + state.toLowerCase();
                
                // Find market profile (implementation would need market profile mapping logic)
                row.add(city + ", " + state); // market area
                for (int i = 0; i < 17; i++) row.add(""); // Market profile fields
            } else {
                for (int i = 0; i < 18; i++) row.add("");
            }
        } else {
            for (int i = 0; i < 18; i++) row.add("");
        }
        
        // US Cities Data
        if (property != null) {
            Document address = (Document) property.get("address");
            if (address != null) {
                String city = safeGetString(address, "city");
                String state = safeGetString(address, "state");
                String cityKey = city.toLowerCase() + "|" + state.toLowerCase();
                
                Document usCityData = usCitiesMap.get(cityKey);
                if (usCityData != null) {
                    row.add(safeGetString(usCityData, "population"));
                    row.add(safeGetString(usCityData, "medianIncome"));
                    row.add(safeGetString(usCityData, "medianHomeValue"));
                    row.add(safeGetString(usCityData, "unemploymentRate"));
                    row.add(safeGetString(usCityData, "costOfLivingIndex"));
                    row.add(safeGetString(usCityData, "crimeRate"));
                    row.add(safeGetString(usCityData, "schoolRating"));
                    row.add(safeGetString(usCityData, "walkScore"));
                    row.add(safeGetString(usCityData, "transitScore"));
                } else {
                    for (int i = 0; i < 9; i++) row.add("");
                }
            } else {
                for (int i = 0; i < 9; i++) row.add("");
            }
        } else {
            for (int i = 0; i < 9; i++) row.add("");
        }
        
        // Enhanced Tags and Categories from listingSearch
        Document listingSearch = listingSearchMap.get(listingId);
        if (listingSearch != null) {
            // Get lifestyle names
            List<String> lifestyleNames = safeGetStringList(listingSearch, "lifestyleNames");
            row.add(String.join(",", lifestyleNames));
            
            // Get tag names
            List<String> tagNames = safeGetStringList(listingSearch, "tagNames");
            row.add(String.join(",", tagNames));
            
            row.add(safeGetString(listingSearch, "coOwnershipType"));
            
            // Get tag categories
            List<String> tagCategories = safeGetStringList(listingSearch, "tagCategories");
            row.add(String.join(",", tagCategories));
            
            row.add(safeGetString(listingSearch, "tagWeightsSum"));
        } else {
            for (int i = 0; i < 5; i++) row.add("");
        }
        
        // Feeder Markets (placeholder - implementation would need feeder market logic)
        for (int i = 0; i < 4; i++) row.add("");
        
        return row.toArray(new String[0]);
    }
    
    private String categorizeStatus(String status) {
        if (status == null) return "Unknown";
        String lower = status.toLowerCase();
        if (lower.contains("active") || lower.contains("for sale")) return "Active";
        if (lower.contains("pending") || lower.contains("under contract")) return "Pending";
        if (lower.contains("sold") || lower.contains("closed")) return "Sold";
        if (lower.contains("expired") || lower.contains("cancelled") || lower.contains("withdrawn")) return "Expired";
        return "Other";
    }
    
    private String safeGetString(Document doc, String field) {
        if (doc == null) return "";
        Object value = doc.get(field);
        return value != null ? value.toString() : "";
    }
    
    private Double safeGetDouble(Document doc, String field) {
        if (doc == null) return null;
        Object value = doc.get(field);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }
    
    private List<String> safeGetStringList(Document doc, String field) {
        if (doc == null) return new ArrayList<>();
        Object value = doc.get(field);
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return new ArrayList<>();
    }
    
    private void collectRowStatistics(String[] row) {
        for (int i = 0; i < row.length; i++) {
            String value = row[i];
            if (value == null || value.trim().isEmpty()) {
                nullCounts.put(i, nullCounts.getOrDefault(i, 0) + 1);
            } else {
                Map<String, Integer> valueMap = columnStats.get(i);
                if (valueMap == null) {
                    valueMap = new HashMap<>();
                    columnStats.put(i, valueMap);
                }
                valueMap.put(value, valueMap.getOrDefault(value, 0) + 1);
            }
        }
    }
    
    private void writeStatisticsFile(String statsPath, String[] headers) {
        try (FileWriter writer = new FileWriter(statsPath)) {
            writer.write("COLUMN STATISTICS REPORT\n");
            writer.write("========================\n");
            writer.write("Total rows analyzed: " + totalRows + "\n\n");
            
            writer.write("EMPTY COLUMNS (100% null/empty):\n");
            writer.write("---------------------------------\n");
            
            List<String> emptyColumns = new ArrayList<>();
            List<String> singleValueColumns = new ArrayList<>();
            
            for (int i = 0; i < headers.length; i++) {
                int nullCount = nullCounts.get(i);
                Map<String, Integer> valueMap = columnStats.get(i);
                
                if (nullCount == totalRows) {
                    emptyColumns.add(String.format("Column %d: %s", i, headers[i]));
                } else if (valueMap.size() == 1 && nullCount == 0) {
                    String singleValue = valueMap.keySet().iterator().next();
                    singleValueColumns.add(String.format("Column %d: %s = '%s'", i, headers[i], singleValue));
                }
            }
            
            for (String col : emptyColumns) {
                writer.write(col + "\n");
            }
            
            writer.write("\nSINGLE VALUE COLUMNS (only one non-null distinct value):\n");
            writer.write("--------------------------------------------------------\n");
            
            for (String col : singleValueColumns) {
                writer.write(col + "\n");
            }
            
            writer.write("\nDETAILED COLUMN STATISTICS:\n");
            writer.write("---------------------------\n");
            
            for (int i = 0; i < headers.length; i++) {
                writer.write(String.format("\nColumn %d: %s\n", i, headers[i]));
                writer.write(String.format("  Null/Empty count: %d (%.2f%%)\n", 
                    nullCounts.get(i), (nullCounts.get(i) * 100.0) / totalRows));
                
                Map<String, Integer> valueMap = columnStats.get(i);
                writer.write(String.format("  Distinct values: %d\n", valueMap.size()));
                
                if (valueMap.size() > 0 && valueMap.size() <= 10) {
                    writer.write("  Value distribution:\n");
                    valueMap.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(10)
                        .forEach(entry -> {
                            try {
                                writer.write(String.format("    '%s': %d (%.2f%%)\n", 
                                    entry.getKey().length() > 50 ? entry.getKey().substring(0, 50) + "..." : entry.getKey(),
                                    entry.getValue(), 
                                    (entry.getValue() * 100.0) / totalRows));
                            } catch (IOException e) {
                                logger.error("Error writing statistics", e);
                            }
                        });
                }
            }
            
            logger.info("Statistics written to: {}", statsPath);
            
        } catch (IOException e) {
            logger.error("Failed to write statistics file", e);
        }
    }
}