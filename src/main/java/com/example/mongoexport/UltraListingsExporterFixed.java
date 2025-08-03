package com.example.mongoexport;

import com.mongodb.client.*;
import com.opencsv.CSVWriter;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class UltraListingsExporterFixed {
    private static final Logger logger = LoggerFactory.getLogger(UltraListingsExporterFixed.class);
    private static final int BATCH_SIZE = 2000; // Process 2000 listings at a time with more memory
    
    private final ExportConfig config;
    private final MongoDatabase database;
    
    // With 20GB available, we can load more collections into memory
    private Map<ObjectId, Document> brokeragesMap = new HashMap<>();
    private Map<ObjectId, Document> currentAgentsMap = new HashMap<>();
    private Map<ObjectId, Document> peopleMap = new HashMap<>();
    private Map<ObjectId, List<Document>> transactionsByListingMap = new HashMap<>();
    
    // Additional collections for enhanced attributes
    private Map<ObjectId, Document> marketProfilesMap = new HashMap<>();
    private Map<String, Document> usCitiesMap = new HashMap<>(); // Key by city+state
    private Map<ObjectId, Document> usCitiesById = new HashMap<>(); // Add mapping by ObjectId for numeric city lookups
    private Map<Integer, Document> usCitiesByNumericId = new HashMap<>(); // Add mapping by numeric ID
    private Map<ObjectId, Document> listingSearchMap = new HashMap<>();
    private Map<ObjectId, Document> tagsMap = new HashMap<>();
    private Map<String, List<Document>> feederMarketsMap = new HashMap<>(); // Key by city+state
    
    // Common lifestyle and tag values found in the database
    private static final List<String> COMMON_LIFESTYLES = Arrays.asList("urban", "suburban", "luxury", "waterfront", "golf", "equestrian", "ski", "beach", "mountain", "resort", "active", "family", "retirement", "investment", "vacation", "eco_friendly", "historic", "gated", "downtown", "country", "lakefront", "riverfront", "oceanfront", "vineyard", "ranch");
    
    private static final List<String> COMMON_PROPERTY_TAGS = Arrays.asList("pool", "spa", "tennis", "gym", "security", "concierge", "elevator", "parking", "views", "updated", "new_construction", "fixer", "foreclosure", "short_sale", "reo", "auction", "coming_soon", "price_reduced", "open_house", "virtual_tour", "video_tour", "3d_tour", "motivated_seller", "cash_only");
    
    private static final List<String> COMMON_FEATURES = Arrays.asList("hardwood_floors", "granite_counters", "stainless_appliances", "master_downstairs", "game_room", "media_room", "wine_cellar", "smart_home", "solar_panels", "guest_house", "workshop", "rv_parking", "boat_access", "private_beach", "dock", "acreage", "corner_lot", "cul_de_sac", "gated_entry", "circular_driveway", "three_car_garage", "walk_in_closet", "jetted_tub", "dual_sinks", "kitchen_island");
    
    public UltraListingsExporterFixed() {
        this.config = new ExportConfig();
        MongoClient mongoClient = MongoClients.create(config.getMongoUrl());
        this.database = mongoClient.getDatabase(config.getDatabaseName());
    }
    
    public static void main(String[] args) {
        UltraListingsExporterFixed exporter = new UltraListingsExporterFixed();
        
        logger.info("Starting super-optimized comprehensive export with city ID fix...");
        
        // Count listings by status first
        MongoCollection<Document> listings = exporter.database.getCollection("listings");
        long totalListings = listings.countDocuments();
        logger.info("Total listings in database: {}", totalListings);
        
        exporter.exportAllListingsUltraComprehensive();
        logger.info("Ultra comprehensive export with city ID fix completed!");
    }
    
    private void loadCollectionsIntoMemory() {
        logger.info("Loading collections into memory for fast lookups (with 20GB available)...");
        
        // Load currentAgents (28K docs)
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
        long transCount = 0;
        for (Document doc : transactions.find()) {
            Object listingRef = doc.get("listing");
            if (listingRef instanceof ObjectId) {
                ObjectId listingId = (ObjectId) listingRef;
                transactionsByListingMap.computeIfAbsent(listingId, k -> new ArrayList<>()).add(doc);
                transCount++;
            }
        }
        logger.info("Loaded {} transactions into memory", transCount);
        
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
        
        // Load US cities (7.8K docs) with multiple mappings
        logger.info("Loading US cities collection with multiple mappings...");
        MongoCollection<Document> usCities = database.getCollection("usCities");
        for (Document doc : usCities.find()) {
            // Original mapping by city+state
            String city = doc.getString("city");
            String state = doc.getString("state");
            if (city != null && state != null) {
                String key = city.toLowerCase() + "|" + state.toLowerCase();
                usCitiesMap.put(key, doc);
            }
            
            // New mapping by ObjectId
            ObjectId id = doc.getObjectId("_id");
            if (id != null) {
                usCitiesById.put(id, doc);
            }
            
            // Check if there's a numeric ID field that might be used for city references
            Object cityId = doc.get("cityId");
            if (cityId instanceof Integer) {
                usCitiesByNumericId.put((Integer) cityId, doc);
            } else if (cityId instanceof Long) {
                usCitiesByNumericId.put(((Long) cityId).intValue(), doc);
            }
            
            // Also check for just "id" field
            Object numId = doc.get("id");
            if (numId instanceof Integer) {
                usCitiesByNumericId.put((Integer) numId, doc);
            } else if (numId instanceof Long) {
                usCitiesByNumericId.put(((Long) numId).intValue(), doc);
            }
        }
        logger.info("Loaded {} US cities into memory (by name: {}, by ObjectId: {}, by numeric ID: {})", 
            usCitiesMap.size() + usCitiesById.size() + usCitiesByNumericId.size(),
            usCitiesMap.size(), usCitiesById.size(), usCitiesByNumericId.size());
        
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
    
    /**
     * Resolves a city field that could be numeric ID, ObjectId, or string
     */
    private String resolveCityField(Object cityValue) {
        if (cityValue == null) {
            return "";
        }
        
        // If it's already a string, return it
        if (cityValue instanceof String) {
            return (String) cityValue;
        }
        
        // If it's a number, try to look it up in our numeric city map
        if (cityValue instanceof Number) {
            int cityId = ((Number) cityValue).intValue();
            Document cityDoc = usCitiesByNumericId.get(cityId);
            if (cityDoc != null) {
                String cityName = cityDoc.getString("city");
                if (cityName != null) {
                    logger.debug("Resolved numeric city ID {} to {}", cityId, cityName);
                    return cityName;
                }
            }
            logger.warn("Could not resolve numeric city ID: {}", cityId);
            return String.valueOf(cityId); // Return the number as string if not found
        }
        
        // If it's an ObjectId, try to look it up
        if (cityValue instanceof ObjectId) {
            ObjectId cityOid = (ObjectId) cityValue;
            Document cityDoc = usCitiesById.get(cityOid);
            if (cityDoc != null) {
                String cityName = cityDoc.getString("city");
                if (cityName != null) {
                    logger.debug("Resolved ObjectId city {} to {}", cityOid, cityName);
                    return cityName;
                }
            }
            logger.warn("Could not resolve ObjectId city: {}", cityOid);
            return cityOid.toString(); // Return ObjectId as string if not found
        }
        
        // For any other type, convert to string
        return cityValue.toString();
    }
    
    public void exportAllListingsUltraComprehensive() {
        logger.info("Exporting ALL listings with ultra-comprehensive fields and city ID resolution...");
        
        // Load collections into memory
        loadCollectionsIntoMemory();
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String outputPath = config.getOutputDirectory() + "/all_listings_ultra_comprehensive_fixed_" + timestamp + ".csv";
        
        try (FileWriter fileWriter = new FileWriter(outputPath); 
             CSVWriter csvWriter = new CSVWriter(fileWriter)) {
            
            // Write comprehensive headers
            csvWriter.writeNext(buildComprehensiveHeaders());
            
            MongoCollection<Document> listings = database.getCollection("listings");
            MongoCollection<Document> properties = database.getCollection("properties");
            MongoCollection<Document> currentAgents = database.getCollection("currentAgents");
            MongoCollection<Document> people = database.getCollection("people");
            MongoCollection<Document> transactions = database.getCollection("transactions");
            
            int totalCount = 0;
            long startTime = System.currentTimeMillis();
            
            // Process listings in batches
            List<Document> batch = new ArrayList<>();
            
            try (MongoCursor<Document> cursor = listings.find().iterator()) {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    
                    // When batch is full, process it
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
                        
                        // Batch load all needed data
                        Map<ObjectId, Document> batchProperties = new HashMap<>();
                        if (!propertyIds.isEmpty()) {
                            properties.find(new Document("_id", new Document("$in", new ArrayList<>(propertyIds))))
                                .forEach(doc -> {
                                    ObjectId id = doc.getObjectId("_id");
                                    if (id != null) {
                                        batchProperties.put(id, doc);
                                    }
                                });
                        }
                        
                        Map<ObjectId, Document> batchAgents = new HashMap<>();
                        Map<ObjectId, Document> batchPeople = new HashMap<>();
                        if (!currentAgentIds.isEmpty()) {
                            // Load currentAgents
                            currentAgents.find(new Document("_id", new Document("$in", new ArrayList<>(currentAgentIds))))
                                .forEach(doc -> {
                                    ObjectId id = doc.getObjectId("_id");
                                    if (id != null) {
                                        batchAgents.put(id, doc);
                                    }
                                });
                            
                            // Collect person IDs from currentAgents
                            Set<ObjectId> personIds = new HashSet<>();
                            for (Document currentAgent : batchAgents.values()) {
                                Object personRef = currentAgent.get("person");
                                if (personRef instanceof ObjectId) {
                                    personIds.add((ObjectId) personRef);
                                }
                            }
                            
                            // Load people
                            if (!personIds.isEmpty()) {
                                people.find(new Document("_id", new Document("$in", new ArrayList<>(personIds))))
                                    .forEach(doc -> {
                                        ObjectId id = doc.getObjectId("_id");
                                        if (id != null) {
                                            batchPeople.put(id, doc);
                                        }
                                    });
                            }
                        }
                        
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
                        
                        // Process batch and write rows
                        for (Document listing : batch) {
                            // Get all related documents first
                            Document property = null;
                            Object propertyRef = listing.get("property");
                            if (propertyRef instanceof ObjectId) {
                                property = batchProperties.get((ObjectId) propertyRef);
                            }
                            
                            Document currentAgent = null;
                            Document currentAgentPerson = null;
                            Object currentAgentRef = listing.get("currentAgentId");
                            if (currentAgentRef instanceof ObjectId) {
                                currentAgent = currentAgentsMap.get((ObjectId) currentAgentRef);
                                if (currentAgent != null) {
                                    Object personRef = currentAgent.get("person");
                                    if (personRef instanceof ObjectId) {
                                        currentAgentPerson = peopleMap.get((ObjectId) personRef);
                                    }
                                }
                            }
                            
                            Document brokerage = null;
                            if (currentAgent != null) {
                                Document realmData = (Document) currentAgent.get("realmData");
                                if (realmData != null) {
                                    Object brokeragesObj = realmData.get("brokerages");
                                    if (brokeragesObj instanceof List) {
                                        List<?> brokerageList = (List<?>) brokeragesObj;
                                        if (!brokerageList.isEmpty()) {
                                            Object firstBrokerage = brokerageList.get(0);
                                            if (firstBrokerage instanceof Document) {
                                                Document brokerageInfo = (Document) firstBrokerage;
                                                Object brokerageRef = brokerageInfo.get("_id");
                                                if (brokerageRef instanceof ObjectId) {
                                                    brokerage = brokeragesMap.get((ObjectId) brokerageRef);
                                                }
                                            } else if (firstBrokerage instanceof ObjectId) {
                                                brokerage = brokeragesMap.get((ObjectId) firstBrokerage);
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Document transaction = null;
                            Object listingId = listing.get("_id");
                            if (listingId instanceof ObjectId) {
                                List<Document> listingTransactions = transactionsByListingMap.get((ObjectId) listingId);
                                if (listingTransactions != null && !listingTransactions.isEmpty()) {
                                    transaction = listingTransactions.get(0);
                                }
                            }
                            
                            // Build comprehensive row
                            String[] row = buildComprehensiveRow(listing, property, currentAgent, currentAgentPerson, brokerage, transaction);
                            csvWriter.writeNext(row);
                            totalCount++;
                        }
                        
                        // Report progress
                        long currentTime = System.currentTimeMillis();
                        double secondsElapsed = (currentTime - startTime) / 1000.0;
                        double rate = totalCount / secondsElapsed;
                        logger.info("Processed {} listings... ({} listings/sec, batch size: {})", 
                            totalCount, String.format("%.1f", rate), batch.size());
                        
                        // Clear batch and maps
                        batch.clear();
                        batchProperties.clear();
                        batchAgents.clear();
                        batchPeople.clear();
                        batchTransactions.clear();
                        
                        // Force garbage collection every 10 batches to keep memory usage low
                        if (totalCount % (BATCH_SIZE * 10) == 0) {
                            System.gc();
                        }
                    }
                }
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            double totalSeconds = totalTime / 1000.0;
            logger.info("Export completed: {} total listings written to {}", totalCount, outputPath);
            logger.info("Total time: {} seconds ({} listings/sec)", 
                String.format("%.1f", totalSeconds), String.format("%.1f", totalCount / totalSeconds));
            
        } catch (IOException e) {
            logger.error("Failed to export listings", e);
        }
    }
    
    private String safeGetString(Document doc, String field) {
        if (doc == null) return "";
        Object value = doc.get(field);
        return value != null ? value.toString() : "";
    }
    
    private String[] buildComprehensiveHeaders() {
        List<String> headers = new ArrayList<>();
        
        // Listing core fields
        headers.addAll(Arrays.asList("listing_id", "mls_number", "status", "status_category", "list_date", 
            "days_on_market", "list_price", "original_price", "price_change_count", "price_per_sqft", 
            "estimated_monthly_payment", "hoa_fee", "listing_type", "listing_agreement", "commission_percent", 
            "showing_requirements"));
            
        // Property core fields
        headers.addAll(Arrays.asList("property_id", "property_type", "property_subtype", "property_style", 
            "year_built", "year_renovated", "bedrooms", "bathrooms", "half_baths", "total_baths", 
            "living_area", "lot_size", "lot_size_acres", "stories", "total_rooms", "garage_spaces", 
            "carport_spaces", "parking_spaces"));
            
        // Property features
        headers.addAll(Arrays.asList("has_pool", "has_spa", "has_hot_tub", "has_sauna", "has_fireplace", 
            "fireplace_count", "has_basement", "basement_finished", "has_attic", "has_garage", "has_carport", 
            "has_rv_parking", "has_boat_parking", "has_guest_house", "has_mother_in_law", "has_workshop", 
            "has_shed"));
            
        // Extended features as boolean indicators
        for (String feature : COMMON_FEATURES) {
            headers.add("feature_" + feature);
        }
        
        // Location fields
        headers.addAll(Arrays.asList("full_address", "street_number", "street_name", "street_suffix", 
            "unit_number", "city", "state", "zipcode", "zip_plus4", "county", "neighborhood", "subdivision", 
            "latitude", "longitude", "map_precision", "cross_streets", "directions", "location_description"));
            
        // School information
        headers.addAll(Arrays.asList("elementary_school", "elementary_rating", "elementary_distance", 
            "middle_school", "middle_rating", "middle_distance", "high_school", "high_rating", 
            "high_distance", "school_district", "total_schools_nearby", "private_schools_nearby"));
            
        // Lifestyle indicators
        for (String lifestyle : COMMON_LIFESTYLES) {
            headers.add("lifestyle_" + lifestyle);
        }
        
        // Tag indicators
        for (String tag : COMMON_PROPERTY_TAGS) {
            headers.add("tag_" + tag);
        }
        
        // Agent information
        headers.addAll(Arrays.asList("currentAgent_id", "currentAgent_name", "currentAgent_first_name", "currentAgent_last_name", 
            "currentAgent_email", "currentAgent_phone", "currentAgent_mobile", "currentAgent_office_phone", "currentAgent_license", 
            "currentAgent_license_state", "currentAgent_designation", "currentAgent_years_experience", "currentAgent_website", 
            "currentAgent_social_media", "currentAgent_photo_url", "currentAgent_bio_length"));
            
        // Agent from people collection
        headers.addAll(Arrays.asList("currentAgent_person_id", "currentAgent_full_address", "currentAgent_city", "currentAgent_state", 
            "currentAgent_zipcode", "currentAgent_languages", "currentAgent_specialties"));
            
        // Brokerage information
        headers.addAll(Arrays.asList("brokerage_id", "brokerage_name", "brokerage_phone", "brokerage_email", 
            "brokerage_website", "brokerage_address", "brokerage_city", "brokerage_state", 
            "brokerage_zipcode", "brokerage_type", "brokerage_year_established"));
            
        // Marketing and media
        headers.addAll(Arrays.asList("picture_count", "virtual_tour_url", "video_url", 
            "walkthrough_video_url", "drone_video_url", "matterport_url", "marketing_remarks", 
            "marketing_remarks_length", "private_remarks", "private_remarks_length", 
            "showing_instructions", "showing_contact", "lockbox_type", "lockbox_code", 
            "gate_code", "alarm_info"));
            
        // Open house information
        headers.addAll(Arrays.asList("has_open_house", "next_open_house_date", "open_house_comments", 
            "total_open_houses", "last_open_house_date"));
            
        // Financial and tax information
        headers.addAll(Arrays.asList("tax_year", "tax_amount", "tax_rate", "assessed_value", 
            "assessment_year", "homestead_exemption", "senior_exemption", "veteran_exemption", 
            "agricultural_exemption", "tax_id", "legal_description"));
            
        // Transaction information
        headers.addAll(Arrays.asList("transaction_id", "sold_price", "sold_date", "days_to_sell", 
            "price_drop_percent", "buyer_currentAgent_id", "buyer_currentAgent_name", "buyer_brokerage", 
            "financing_type", "concessions_amount", "closing_costs_paid_by_seller"));
            
        // Additional metrics
        headers.addAll(Arrays.asList("walk_score", "transit_score", "bike_score", "climate_risk_score", 
            "flood_zone", "fire_zone", "earthquake_zone", "crime_rate", "school_rating_average", 
            "nearby_amenities_count"));
            
        // Market Profile Demographics
        headers.addAll(Arrays.asList("market_profile_primary_age_group", "market_profile_primary_income_bracket",
            "market_profile_primary_marital_status", "market_profile_buyer_count",
            "market_profile_median_age", "market_profile_median_income"));
            
        // City Statistics
        headers.addAll(Arrays.asList("city_population", "city_median_income", "city_median_home_value",
            "city_growth_rate", "city_unemployment_rate", "city_classification"));
            
        // Enhanced Tags and Categories
        headers.addAll(Arrays.asList("listing_search_lifestyles", "listing_search_tags", 
            "co_ownership_type", "tag_categories", "tag_weights_sum"));
            
        return headers.toArray(new String[0]);
    }
    
    private String[] buildComprehensiveRow(Document listing, Document property, Document currentAgent, 
                                          Document currentAgentPerson, Document brokerage, Document transaction) {
        List<String> row = new ArrayList<>();
        
        // Listing core fields
        row.add(safeGetString(listing, "_id"));
        row.add(safeGetString(listing, "mlsNumber"));
        row.add(safeGetString(listing, "status"));
        row.add(categorizeStatus(safeGetString(listing, "status")));
        
        // List date and days on market
        Date listDate = (Date) listing.get("listDate");
        if (listDate != null) {
            row.add(listDate.toString());
            long daysOnMarket = (System.currentTimeMillis() - listDate.getTime()) / (1000 * 60 * 60 * 24);
            row.add(String.valueOf(daysOnMarket));
        } else {
            row.add("");
            row.add("");
        }
        
        // Pricing information
        Double listPrice = safeGetDouble(listing, "price");
        Double originalPrice = safeGetDouble(listing, "originalPrice");
        row.add(listPrice != null ? String.format("%.2f", listPrice) : "");
        row.add(originalPrice != null ? String.format("%.2f", originalPrice) : "");
        
        // Price change count
        Object priceHistoryObj = listing.get("priceHistory");
        if (priceHistoryObj instanceof List) {
            row.add(String.valueOf(((List<?>) priceHistoryObj).size()));
        } else {
            row.add("0");
        }
        
        // Price per sqft
        Double sqft = null;
        if (property != null) {
            sqft = safeGetDouble(property, "livingArea");
        }
        if (listPrice != null && sqft != null && sqft > 0) {
            row.add(String.format("%.2f", listPrice / sqft));
        } else {
            row.add("");
        }
        
        // Estimated monthly payment
        if (listPrice != null) {
            double monthlyPayment = calculateMonthlyPayment(listPrice, 0.8, 0.065, 30);
            row.add(String.format("%.2f", monthlyPayment));
        } else {
            row.add("");
        }
        
        row.add(safeGetString(listing, "hoaFee"));
        row.add(safeGetString(listing, "listingType"));
        row.add(safeGetString(listing, "listingAgreement"));
        row.add(safeGetString(listing, "commissionPercent"));
        row.add(safeGetString(listing, "showingRequirements"));
        
        // Property core fields
        if (property != null) {
            row.add(safeGetString(property, "_id"));
            row.add(safeGetString(property, "propertyType"));
            row.add(safeGetString(property, "propertySubType"));
            row.add(safeGetString(property, "propertyStyle"));
            row.add(safeGetString(property, "yearBuilt"));
            row.add(safeGetString(property, "yearRenovated"));
            row.add(safeGetString(property, "bedrooms"));
            row.add(safeGetString(property, "bathrooms"));
            row.add(safeGetString(property, "halfBaths"));
            
            // Calculate total baths
            Double fullBaths = safeGetDouble(property, "bathrooms");
            Double halfBaths = safeGetDouble(property, "halfBaths");
            if (fullBaths != null || halfBaths != null) {
                double total = (fullBaths != null ? fullBaths : 0) + (halfBaths != null ? halfBaths * 0.5 : 0);
                row.add(String.format("%.1f", total));
            } else {
                row.add("");
            }
            
            row.add(safeGetString(property, "livingArea"));
            row.add(safeGetString(property, "lotSize"));
            
            // Lot size in acres
            Double lotSize = safeGetDouble(property, "lotSize");
            if (lotSize != null && lotSize > 0) {
                row.add(String.format("%.2f", lotSize / 43560.0));
            } else {
                row.add("");
            }
            
            row.add(safeGetString(property, "stories"));
            row.add(safeGetString(property, "totalRooms"));
            row.add(safeGetString(property, "garageSpaces"));
            row.add(safeGetString(property, "carportSpaces"));
            row.add(safeGetString(property, "parkingSpaces"));
            
            // Property features - check features object
            Document features = (Document) property.get("features");
            if (features != null) {
                row.add(features.getBoolean("hasPool", false) ? "true" : "false");
                row.add(features.getBoolean("hasSpa", false) ? "true" : "false");
                row.add(features.getBoolean("hasHotTub", false) ? "true" : "false");
                row.add(features.getBoolean("hasSauna", false) ? "true" : "false");
                row.add(features.getBoolean("hasFireplace", false) ? "true" : "false");
                row.add(safeGetString(features, "fireplaceCount"));
                row.add(features.getBoolean("hasBasement", false) ? "true" : "false");
                row.add(features.getBoolean("basementFinished", false) ? "true" : "false");
                row.add(features.getBoolean("hasAttic", false) ? "true" : "false");
                row.add(features.getBoolean("hasGarage", false) ? "true" : "false");
                row.add(features.getBoolean("hasCarport", false) ? "true" : "false");
                row.add(features.getBoolean("hasRvParking", false) ? "true" : "false");
                row.add(features.getBoolean("hasBoatParking", false) ? "true" : "false");
                row.add(features.getBoolean("hasGuestHouse", false) ? "true" : "false");
                row.add(features.getBoolean("hasMotherInLaw", false) ? "true" : "false");
                row.add(features.getBoolean("hasWorkshop", false) ? "true" : "false");
                row.add(features.getBoolean("hasShed", false) ? "true" : "false");
            } else {
                for (int i = 0; i < 17; i++) {
                    row.add("false");
                }
            }
            
            // Extended features as indicators
            String featureText = safeGetString(property, "featuresText").toLowerCase();
            for (String feature : COMMON_FEATURES) {
                boolean hasFeature = featureText.contains(feature.replace("_", " "));
                row.add(hasFeature ? "true" : "false");
            }
            
            // Location fields
            Document address = (Document) property.get("address");
            if (address != null) {
                row.add(safeGetString(address, "fullAddress"));
                row.add(safeGetString(address, "streetNumber"));
                row.add(safeGetString(address, "streetName"));
                row.add(safeGetString(address, "streetSuffix"));
                row.add(safeGetString(address, "unitNumber"));
                row.add(safeGetString(address, "city"));
                row.add(safeGetString(address, "state"));
                row.add(safeGetString(address, "zipcode"));
                row.add(safeGetString(address, "zipPlus4"));
                row.add(safeGetString(address, "county"));
                row.add(safeGetString(address, "neighborhood"));
                row.add(safeGetString(address, "subdivision"));
                
                Document location = (Document) address.get("location");
                if (location != null) {
                    row.add(safeGetString(location, "lat"));
                    row.add(safeGetString(location, "lng"));
                    row.add(safeGetString(location, "precision"));
                } else {
                    row.add("");
                    row.add("");
                    row.add("");
                }
                
                row.add(safeGetString(address, "crossStreets"));
                row.add(safeGetString(address, "directions"));
                row.add(safeGetString(address, "locationDescription"));
            } else {
                for (int i = 0; i < 18; i++) {
                    row.add("");
                }
            }
            
            // Schools - comprehensive implementation
            List<Document> schools = (List<Document>) property.get("schools");
            if (schools != null && !schools.isEmpty()) {
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
            
            // Lifestyle indicators
            List<String> lifestyles = safeGetStringList(property, "lifestyles");
            for (String lifestyle : COMMON_LIFESTYLES) {
                boolean hasLifestyle = lifestyles.stream()
                    .anyMatch(l -> l.toLowerCase().contains(lifestyle));
                row.add(hasLifestyle ? "true" : "false");
            }
            
            // Tag indicators
            String tagText = safeGetStringList(property, "tags").stream()
                .map(String::toLowerCase)
                .collect(Collectors.joining(" "));
            for (String tag : COMMON_PROPERTY_TAGS) {
                boolean hasTag = tagText.contains(tag.replace("_", " "));
                row.add(hasTag ? "true" : "false");
            }
        } else {
            // Add empty fields for all property-related columns
            int propertyFieldCount = 18 + 17 + COMMON_FEATURES.size() + 18 + 12 + 
                                    COMMON_LIFESTYLES.size() + COMMON_PROPERTY_TAGS.size();
            for (int i = 0; i < propertyFieldCount; i++) {
                row.add("");
            }
        }
        
        // Agent information - comprehensive implementation
        if (listing != null) {
            Object currentAgentRef = listing.get("currentAgentId");
            if (currentAgentRef instanceof ObjectId) {
                Document agentDoc = currentAgentsMap.get((ObjectId) currentAgentRef);
                if (agentDoc != null) {
                    // Basic currentAgent info
                    row.add(safeGetString(agentDoc, "_id"));
                    row.add(safeGetString(agentDoc, "fullName"));
                    
                    String fullName = safeGetString(agentDoc, "fullName");
                    String[] nameParts = fullName.split(" ");
                    row.add(nameParts.length > 0 ? nameParts[0] : ""); // first name
                    row.add(nameParts.length > 1 ? nameParts[nameParts.length - 1] : ""); // last name
                    
                    // Agent realm data
                    Document realmData = (Document) agentDoc.get("realmData");
                    if (realmData != null) {
                        row.add(safeGetString(realmData, "email"));
                        row.add(safeGetString(realmData, "phone"));
                        row.add(safeGetString(realmData, "mobilePhone"));
                        row.add(safeGetString(realmData, "officePhone"));
                        row.add(safeGetString(realmData, "licenseNumber"));
                        row.add(safeGetString(realmData, "licenseState"));
                        row.add(safeGetString(realmData, "designation"));
                        
                        // Calculate years experience
                        Date startDate = (Date) realmData.get("professionalStartDate");
                        if (startDate != null) {
                            long years = (System.currentTimeMillis() - startDate.getTime()) / (1000L * 60 * 60 * 24 * 365);
                            row.add(String.valueOf(years));
                        } else {
                            row.add("");
                        }
                        
                        row.add(safeGetString(realmData, "website"));
                        row.add(safeGetString(realmData, "socialMedia"));
                    } else {
                        for (int i = 0; i < 10; i++) row.add("");
                    }
                    
                    row.add(safeGetString(agentDoc, "photoURL"));
                    
                    // Bio length
                    String bio = safeGetString(agentDoc, "bio");
                    row.add(String.valueOf(bio.length()));
                    
                    // Agent from people collection
                    Object personRef = agentDoc.get("person");
                    if (personRef instanceof ObjectId) {
                        Document person = peopleMap.get((ObjectId) personRef);
                        if (person != null) {
                            row.add(safeGetString(person, "_id"));
                            
                            Document address = (Document) person.get("address");
                            if (address != null) {
                                row.add(safeGetString(address, "fullAddress"));
                                row.add(safeGetString(address, "city"));
                                row.add(safeGetString(address, "state"));
                                row.add(safeGetString(address, "zipcode"));
                            } else {
                                for (int i = 0; i < 4; i++) row.add("");
                            }
                            
                            List<String> languages = safeGetStringList(person, "languages");
                            row.add(String.join(",", languages));
                            
                            List<String> specialties = safeGetStringList(person, "specialties");
                            row.add(String.join(",", specialties));
                        } else {
                            for (int i = 0; i < 7; i++) row.add("");
                        }
                    } else {
                        for (int i = 0; i < 7; i++) row.add("");
                    }
                    
                    // Brokerage information with city ID resolution
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
                                        row.add(safeGetString(currentAgentBrokerage, "phone"));
                                        row.add(safeGetString(currentAgentBrokerage, "email"));
                                        row.add(safeGetString(currentAgentBrokerage, "website"));
                                        row.add(safeGetString(currentAgentBrokerage, "address"));
                                        
                                        // Handle city field that might be numeric ID
                                        Object cityValue = currentAgentBrokerage.get("city");
                                        String resolvedCity = resolveCityField(cityValue);
                                        row.add(resolvedCity);
                                        
                                        row.add(safeGetString(currentAgentBrokerage, "state"));
                                        row.add(safeGetString(currentAgentBrokerage, "zipcode"));
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
            } else {
                // No currentAgent reference - add empty fields for all currentAgent columns
                for (int i = 0; i < 29; i++) row.add("");
            }
        } else {
            // No listing - add empty fields for all currentAgent columns
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
        
        String marketingRemarks = safeGetString(listing, "marketingRemarks");
        row.add(marketingRemarks);
        row.add(String.valueOf(marketingRemarks.length()));
        
        String privateRemarks = safeGetString(listing, "privateRemarks");
        row.add(privateRemarks);
        row.add(String.valueOf(privateRemarks.length()));
        
        row.add(safeGetString(listing, "showingInstructions"));
        row.add(safeGetString(listing, "showingContact"));
        row.add(safeGetString(listing, "lockboxType"));
        row.add(safeGetString(listing, "lockboxCode"));
        row.add(safeGetString(listing, "gateCode"));
        row.add(safeGetString(listing, "alarmInfo"));
        
        // Open house information
        Object openHousesObj = listing.get("openHouses");
        if (openHousesObj instanceof List && !((List<?>) openHousesObj).isEmpty()) {
            List<?> openHouses = (List<?>) openHousesObj;
            row.add("true");
            row.add(safeGetString((Document) openHouses.get(0), "date"));
            row.add(safeGetString((Document) openHouses.get(0), "comments"));
            row.add(String.valueOf(openHouses.size()));
            row.add(safeGetString((Document) openHouses.get(openHouses.size() - 1), "date"));
        } else {
            row.add("false");
            for (int i = 0; i < 4; i++) row.add("");
        }
        
        // Financial and tax information
        if (property != null) {
            Document tax = (Document) property.get("tax");
            if (tax != null) {
                row.add(safeGetString(tax, "year"));
                row.add(safeGetString(tax, "amount"));
                row.add(safeGetString(tax, "rate"));
                row.add(safeGetString(tax, "assessedValue"));
                row.add(safeGetString(tax, "assessmentYear"));
                row.add(safeGetString(tax, "homesteadExemption"));
                row.add(safeGetString(tax, "seniorExemption"));
                row.add(safeGetString(tax, "veteranExemption"));
                row.add(safeGetString(tax, "agriculturalExemption"));
                row.add(safeGetString(tax, "taxId"));
                row.add(safeGetString(tax, "legalDescription"));
            } else {
                for (int i = 0; i < 11; i++) row.add("");
            }
        } else {
            for (int i = 0; i < 11; i++) row.add("");
        }
        
        // Transaction information
        if (transaction != null) {
            row.add(safeGetString(transaction, "_id"));
            row.add(safeGetString(transaction, "soldPrice"));
            row.add(safeGetString(transaction, "soldDate"));
            
            // Calculate days to sell
            Date soldDate = (Date) transaction.get("soldDate");
            Date transactionListDate = (Date) listing.get("listDate");
            if (soldDate != null && transactionListDate != null) {
                long daysToSell = (soldDate.getTime() - transactionListDate.getTime()) / (1000 * 60 * 60 * 24);
                row.add(String.valueOf(daysToSell));
            } else {
                row.add("");
            }
            
            // Price drop percent
            Double soldPrice = safeGetDouble(transaction, "soldPrice");
            Double transactionOriginalPrice = safeGetDouble(listing, "originalPrice");
            if (soldPrice != null && transactionOriginalPrice != null && transactionOriginalPrice > 0) {
                double dropPercent = ((transactionOriginalPrice - soldPrice) / transactionOriginalPrice) * 100;
                row.add(String.format("%.2f", dropPercent));
            } else {
                row.add("");
            }
            
            row.add(safeGetString(transaction, "buyerAgentId"));
            row.add(safeGetString(transaction, "buyerAgentName"));
            row.add(safeGetString(transaction, "buyerBrokerage"));
            row.add(safeGetString(transaction, "financingType"));
            row.add(safeGetString(transaction, "concessionsAmount"));
            row.add(safeGetString(transaction, "closingCostsPaidBySeller"));
        } else {
            for (int i = 0; i < 10; i++) row.add("");
        }
        
        // Additional metrics (placeholder implementation)
        for (int i = 0; i < 10; i++) row.add("");
        
        // Market Profile Demographics
        ObjectId listingId = listing.getObjectId("_id");
        Document marketProfile = marketProfilesMap.get(listingId);
        if (marketProfile != null) {
            // Get primary demographics
            List<Document> ageDist = (List<Document>) marketProfile.get("primaryAgeDistribution");
            if (ageDist != null && !ageDist.isEmpty()) {
                row.add(safeGetString(ageDist.get(0), "ageGroup"));
            } else {
                row.add("");
            }
            
            List<Document> incomeDist = (List<Document>) marketProfile.get("primaryIncomeDistribution");
            if (incomeDist != null && !incomeDist.isEmpty()) {
                row.add(safeGetString(incomeDist.get(0), "incomeBracket"));
            } else {
                row.add("");
            }
            
            List<Document> maritalDist = (List<Document>) marketProfile.get("primaryMaritalDistribution");
            if (maritalDist != null && !maritalDist.isEmpty()) {
                row.add(safeGetString(maritalDist.get(0), "maritalStatus"));
            } else {
                row.add("");
            }
            
            row.add(safeGetString(marketProfile, "buyerCount"));
            row.add(safeGetString(marketProfile, "medianAge"));
            row.add(safeGetString(marketProfile, "medianIncome"));
        } else {
            for (int i = 0; i < 6; i++) row.add("");
        }
        
        // City Statistics
        if (property != null) {
            Document address = (Document) property.get("address");
            if (address != null) {
                String city = safeGetString(address, "city");
                String state = safeGetString(address, "state");
                if (city != null && state != null) {
                    String cityKey = city.toLowerCase() + "|" + state.toLowerCase();
                    Document cityData = usCitiesMap.get(cityKey);
                    if (cityData != null) {
                        row.add(safeGetString(cityData, "population"));
                        row.add(safeGetString(cityData, "medianIncome"));
                        row.add(safeGetString(cityData, "medianHomeValue"));
                        row.add(safeGetString(cityData, "growthRate"));
                        row.add(safeGetString(cityData, "unemploymentRate"));
                        row.add(safeGetString(cityData, "classification"));
                    } else {
                        for (int i = 0; i < 6; i++) row.add("");
                    }
                } else {
                    for (int i = 0; i < 6; i++) row.add("");
                }
            } else {
                for (int i = 0; i < 6; i++) row.add("");
            }
        } else {
            for (int i = 0; i < 6; i++) row.add("");
        }
        
        // Enhanced Tags and Categories
        Document listingSearch = listingSearchMap.get(listingId);
        if (listingSearch != null) {
            // Get lifestyle names
            List<String> lifestyleNames = safeGetStringList(listingSearch, "lifestyleNames");
            row.add(String.join(",", lifestyleNames));
            
            // Get tag names
            List<String> tagNames = safeGetStringList(listingSearch, "tagNames");
            row.add(String.join(",", tagNames));
            
            row.add(safeGetString(listingSearch, "coOwnershipType"));
            
            // Get tag categories from tags
            Set<String> tagCategories = new HashSet<>();
            List<ObjectId> tagIds = (List<ObjectId>) listingSearch.get("tags");
            if (tagIds != null) {
                for (ObjectId tagId : tagIds) {
                    Document tag = tagsMap.get(tagId);
                    if (tag != null) {
                        String category = safeGetString(tag, "category");
                        if (!category.isEmpty()) {
                            tagCategories.add(category);
                        }
                    }
                }
            }
            row.add(String.join(",", tagCategories));
            
            row.add(String.valueOf(tagNames.size())); // tag weights sum as count for now
        } else {
            for (int i = 0; i < 5; i++) row.add("");
        }
        
        return row.toArray(new String[0]);
    }
    
    private String categorizeStatus(String status) {
        if (status == null) return "Unknown";
        status = status.toLowerCase();
        
        if (status.contains("active") || status.contains("coming soon")) {
            return "Active";
        } else if (status.contains("pending") || status.contains("under contract")) {
            return "Pending";
        } else if (status.contains("rent")) {
            return "Rental";
        } else if (status.contains("inactive") || status.contains("old") || status.contains("hidden")) {
            return "Inactive";
        } else {
            return "Other";
        }
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
    
    private double calculateMonthlyPayment(double principal, double downPaymentRatio, 
                                          double annualRate, int years) {
        double loanAmount = principal * (1 - downPaymentRatio);
        double monthlyRate = annualRate / 12;
        int months = years * 12;
        
        if (monthlyRate == 0) {
            return loanAmount / months;
        }
        
        return loanAmount * (monthlyRate * Math.pow(1 + monthlyRate, months)) / 
               (Math.pow(1 + monthlyRate, months) - 1);
    }
}