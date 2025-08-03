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

public class UltraTransactionExporter {
    private static final Logger logger = LoggerFactory.getLogger(UltraTransactionExporter.class);
    private static final int BATCH_SIZE = 1000; // Process 1000 transactions at a time
    
    private final ExportConfig config;
    private final MongoDatabase database;
    
    // In-memory lookup maps for smaller collections
    private Map<ObjectId, Document> agentsMap = new HashMap<>();
    private Map<ObjectId, Document> peopleMap = new HashMap<>();
    private Map<ObjectId, Document> brokeragesMap = new HashMap<>();
    
    // Additional collections for enhanced attributes
    private Map<ObjectId, List<Document>> agentClientsByPersonMap = new HashMap<>(); // Key by person ID
    private Map<ObjectId, List<Document>> residencesByPersonMap = new HashMap<>(); // Key by person ID
    private Map<ObjectId, Document> transactionsDerivedMap = new HashMap<>(); // Key by transaction ID
    
    // Common transaction and financing types
    private static final List<String> FINANCING_TYPES = Arrays.asList(
        "conventional", "fha", "va", "cash", "usda", "jumbo", "portfolio", 
        "hard_money", "seller_financing", "assumable", "other"
    );
    
    private static final List<String> PROPERTY_TYPES = Arrays.asList(
        "single_family", "condo", "townhouse", "multi_family", "land", 
        "commercial", "mobile_home", "farm", "other"
    );
    
    public UltraTransactionExporter() {
        this.config = new ExportConfig();
        MongoClient mongoClient = MongoClients.create(config.getMongoUrl());
        this.database = mongoClient.getDatabase(config.getDatabaseName());
    }
    
    public static void main(String[] args) {
        UltraTransactionExporter exporter = new UltraTransactionExporter();
        
        logger.info("Starting ultra-comprehensive transaction history export...");
        
        // Count transactions first
        MongoCollection<Document> transactions = exporter.database.getCollection("transactions");
        long totalTransactions = transactions.countDocuments();
        logger.info("Total transactions in database: {}", totalTransactions);
        
        exporter.exportTransactionHistoryUltraComprehensive();
        logger.info("Ultra comprehensive transaction export completed!");
    }
    
    private void loadCollectionsIntoMemory() {
        logger.info("Loading collections into memory for transaction analysis...");
        
        // Load agents (28K docs)
        logger.info("Loading agents collection...");
        MongoCollection<Document> agents = database.getCollection("agents");
        for (Document doc : agents.find()) {
            ObjectId id = doc.getObjectId("_id");
            if (id != null) {
                agentsMap.put(id, doc);
            }
        }
        logger.info("Loaded {} agents into memory", agentsMap.size());
        
        // Load brokerages
        logger.info("Loading brokerages collection...");
        MongoCollection<Document> brokerages = database.getCollection("brokerages");
        for (Document doc : brokerages.find()) {
            ObjectId id = doc.getObjectId("_id");
            if (id != null) {
                brokeragesMap.put(id, doc);
            }
        }
        logger.info("Loaded {} brokerages into memory", brokeragesMap.size());
        
        // Load people (620K docs)
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
        
        // Load agent clients (571K docs - group by person)
        logger.info("Loading agent clients collection...");
        MongoCollection<Document> agentClients = database.getCollection("agentclients");
        long agentClientCount = 0;
        for (Document doc : agentClients.find()) {
            Object personRef = doc.get("client");
            if (personRef instanceof ObjectId) {
                ObjectId personId = (ObjectId) personRef;
                agentClientsByPersonMap.computeIfAbsent(personId, k -> new ArrayList<>()).add(doc);
                agentClientCount++;
                if (agentClientCount % 100000 == 0) {
                    logger.info("  Loaded {} agent client records...", agentClientCount);
                }
            }
        }
        logger.info("Loaded {} agent client records into memory", agentClientCount);
        
        // Load residences (7K docs)
        logger.info("Loading residences collection...");
        MongoCollection<Document> residences = database.getCollection("residences");
        for (Document doc : residences.find()) {
            Object personRef = doc.get("person");
            if (personRef instanceof ObjectId) {
                ObjectId personId = (ObjectId) personRef;
                residencesByPersonMap.computeIfAbsent(personId, k -> new ArrayList<>()).add(doc);
            }
        }
        logger.info("Loaded {} residences into memory", residencesByPersonMap.size());
        
        // Load transactions derived (24K docs)
        logger.info("Loading transactions derived collection...");
        MongoCollection<Document> transactionsDerived = database.getCollection("transactionsderived");
        for (Document doc : transactionsDerived.find()) {
            ObjectId id = doc.getObjectId("_id");
            if (id != null) {
                transactionsDerivedMap.put(id, doc);
            }
        }
        logger.info("Loaded {} derived transactions into memory", transactionsDerivedMap.size());
        
        // Report memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        logger.info("Memory usage after loading collections: {} MB", usedMemory);
    }
    
    public void exportTransactionHistoryUltraComprehensive() {
        logger.info("Exporting transaction history with ultra-comprehensive fields...");
        
        // Load collections into memory
        loadCollectionsIntoMemory();
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String outputPath = config.getOutputDirectory() + "/transactions_ultra_comprehensive_" + timestamp + ".csv";
        
        try (FileWriter fileWriter = new FileWriter(outputPath); 
             CSVWriter csvWriter = new CSVWriter(fileWriter)) {
            
            // Write comprehensive headers
            csvWriter.writeNext(buildComprehensiveHeaders());
            
            MongoCollection<Document> transactions = database.getCollection("transactions");
            MongoCollection<Document> listings = database.getCollection("listings");
            MongoCollection<Document> properties = database.getCollection("properties");
            
            int totalCount = 0;
            long startTime = System.currentTimeMillis();
            
            // Process transactions in batches
            List<Document> batch = new ArrayList<>();
            
            try (MongoCursor<Document> cursor = transactions.find().iterator()) {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    
                    // When batch is full, process it
                    if (batch.size() >= BATCH_SIZE || !cursor.hasNext()) {
                        // Collect all IDs needed for this batch
                        Set<ObjectId> listingIds = new HashSet<>();
                        Set<ObjectId> propertyIds = new HashSet<>();
                        
                        for (Document transaction : batch) {
                            Object listingRef = transaction.get("listing");
                            if (listingRef instanceof ObjectId) {
                                listingIds.add((ObjectId) listingRef);
                            }
                            
                            Object propertyRef = transaction.get("property");
                            if (propertyRef instanceof ObjectId) {
                                propertyIds.add((ObjectId) propertyRef);
                            }
                        }
                        
                        // Batch load listings
                        Map<ObjectId, Document> batchListings = new HashMap<>();
                        if (!listingIds.isEmpty()) {
                            listings.find(new Document("_id", new Document("$in", new ArrayList<>(listingIds))))
                                .forEach(doc -> {
                                    ObjectId id = doc.getObjectId("_id");
                                    if (id != null) {
                                        batchListings.put(id, doc);
                                        
                                        // Also get property ID from listing
                                        Object propRef = doc.get("property");
                                        if (propRef instanceof ObjectId) {
                                            propertyIds.add((ObjectId) propRef);
                                        }
                                    }
                                });
                        }
                        
                        // Batch load properties
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
                        
                        // Process batch and write rows
                        for (Document transaction : batch) {
                            // Get listing
                            Document listing = null;
                            Object listingRef = transaction.get("listing");
                            if (listingRef instanceof ObjectId) {
                                listing = batchListings.get((ObjectId) listingRef);
                            }
                            
                            // Get property
                            Document property = null;
                            Object propertyRef = transaction.get("property");
                            if (propertyRef instanceof ObjectId) {
                                property = batchProperties.get((ObjectId) propertyRef);
                            } else if (listing != null) {
                                // Try to get property from listing
                                Object propRef = listing.get("property");
                                if (propRef instanceof ObjectId) {
                                    property = batchProperties.get((ObjectId) propRef);
                                }
                            }
                            
                            // Build comprehensive row
                            String[] row = buildComprehensiveRow(transaction, listing, property);
                            csvWriter.writeNext(row);
                            totalCount++;
                        }
                        
                        // Report progress
                        long currentTime = System.currentTimeMillis();
                        double secondsElapsed = (currentTime - startTime) / 1000.0;
                        double rate = totalCount / secondsElapsed;
                        logger.info("Processed {} transactions... ({} transactions/sec, batch size: {})", 
                            totalCount, String.format("%.1f", rate), batch.size());
                        
                        // Clear batch and maps
                        batch.clear();
                        batchListings.clear();
                        batchProperties.clear();
                    }
                }
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            double totalSeconds = totalTime / 1000.0;
            logger.info("Export completed: {} total transactions written to {}", totalCount, outputPath);
            logger.info("Total time: {} seconds ({} transactions/sec)", 
                String.format("%.1f", totalSeconds), String.format("%.1f", totalCount / totalSeconds));
            
        } catch (IOException e) {
            logger.error("Failed to export transactions", e);
        }
    }
    
    private String[] buildComprehensiveHeaders() {
        List<String> headers = new ArrayList<>();
        
        // Transaction core fields
        headers.addAll(Arrays.asList("transaction_id", "closing_date", "sale_price", "list_price",
            "price_difference", "price_ratio", "days_on_market", "financing_type", "loan_amount",
            "loan_type", "down_payment", "down_payment_percent", "commission_amount"));
            
        // Property details
        headers.addAll(Arrays.asList("property_id", "property_type", "year_built", "bedrooms",
            "bathrooms", "living_area", "lot_size", "price_per_sqft", "full_address", "street_number",
            "street_name", "unit_number", "city", "state", "zipcode", "county", "neighborhood",
            "subdivision", "latitude", "longitude"));
            
        // Property features
        headers.addAll(Arrays.asList("has_pool", "has_spa", "has_fireplace", "has_basement",
            "has_garage", "garage_spaces", "has_view", "has_waterfront", "stories", "construction_type",
            "roof_type", "heating_type", "cooling_type", "flooring_type"));
            
        // Listing information
        headers.addAll(Arrays.asList("listing_id", "mls_number", "listing_date", "original_list_price",
            "final_list_price", "price_reductions", "listing_status", "listing_type", "listing_agreement"));
            
        // Buyer information
        headers.addAll(Arrays.asList("buyer_count", "buyer1_id", "buyer1_name", "buyer1_email",
            "buyer1_phone", "buyer1_address", "buyer2_id", "buyer2_name", "buyer_type"));
            
        // Seller information
        headers.addAll(Arrays.asList("seller_count", "seller1_id", "seller1_name", "seller1_email",
            "seller1_phone", "seller1_address", "seller2_id", "seller2_name", "seller_type"));
            
        // Listing agent information
        headers.addAll(Arrays.asList("listing_agent_id", "listing_agent_name", "listing_agent_email",
            "listing_agent_phone", "listing_agent_license", "listing_agent_years_exp",
            "listing_brokerage_id", "listing_brokerage_name", "listing_commission"));
            
        // Buyer agent information
        headers.addAll(Arrays.asList("buyer_agent_id", "buyer_agent_name", "buyer_agent_email",
            "buyer_agent_phone", "buyer_agent_license", "buyer_agent_years_exp",
            "buyer_brokerage_id", "buyer_brokerage_name", "buyer_commission"));
            
        // Transaction parties and details
        headers.addAll(Arrays.asList("escrow_company", "title_company", "closing_costs",
            "seller_concessions", "home_warranty", "inspection_contingency", "financing_contingency",
            "appraisal_contingency", "earnest_money", "possession_date"));
            
        // Market conditions
        headers.addAll(Arrays.asList("market_temperature", "inventory_level", "average_dom",
            "list_to_sale_ratio", "competing_listings", "recent_sales_count"));
            
        // Financing type indicators
        for (String financing : FINANCING_TYPES) {
            headers.add("financing_" + financing);
        }
        
        // Property type indicators
        for (String propType : PROPERTY_TYPES) {
            headers.add("property_type_" + propType);
        }
        
        // Transaction Derived Metrics
        headers.addAll(Arrays.asList("transaction_performance_score", "price_negotiation_percentage",
            "market_adjusted_sale_price", "transaction_complexity_score", "closing_speed_score"));
            
        // Client History
        headers.addAll(Arrays.asList("buyer_previous_purchases_count", "buyer_client_since_date",
            "buyer_total_spent", "seller_previous_sales_count", "seller_client_since_date",
            "is_repeat_client_transaction", "client_loyalty_score"));
            
        // Residence Portfolio
        headers.addAll(Arrays.asList("buyer_total_properties_owned", "buyer_portfolio_value",
            "seller_remaining_properties", "seller_next_residence_type", "is_investment_property_sale",
            "is_downsizing", "is_upsizing"));
            
        return headers.toArray(new String[0]);
    }
    
    private String[] buildComprehensiveRow(Document transaction, Document listing, Document property) {
        List<String> row = new ArrayList<>();
        
        // Transaction core fields
        row.add(safeGetString(transaction, "_id"));
        
        Date closingDate = (Date) transaction.get("closingDate");
        if (closingDate != null) {
            row.add(closingDate.toString());
        } else {
            row.add("");
        }
        
        Double salePrice = safeGetDouble(transaction, "salePrice");
        row.add(salePrice != null ? String.format("%.2f", salePrice) : "");
        
        // Get list price from listing
        Double listPrice = null;
        if (listing != null) {
            listPrice = safeGetDouble(listing, "price");
        }
        row.add(listPrice != null ? String.format("%.2f", listPrice) : "");
        
        // Calculate price difference and ratio
        if (salePrice != null && listPrice != null && listPrice > 0) {
            double difference = salePrice - listPrice;
            double ratio = salePrice / listPrice;
            row.add(String.format("%.2f", difference));
            row.add(String.format("%.4f", ratio));
        } else {
            row.add("");
            row.add("");
        }
        
        // Days on market
        if (listing != null) {
            Date listDate = (Date) listing.get("listDate");
            if (listDate != null && closingDate != null) {
                long days = (closingDate.getTime() - listDate.getTime()) / (1000 * 60 * 60 * 24);
                row.add(String.valueOf(days));
            } else {
                row.add("");
            }
        } else {
            row.add("");
        }
        
        // Financing information
        row.add(safeGetString(transaction, "financingType"));
        
        Double loanAmount = safeGetDouble(transaction, "loanAmount");
        row.add(loanAmount != null ? String.format("%.2f", loanAmount) : "");
        row.add(safeGetString(transaction, "loanType"));
        
        // Calculate down payment
        if (salePrice != null && loanAmount != null) {
            double downPayment = salePrice - loanAmount;
            double downPaymentPercent = (downPayment / salePrice) * 100;
            row.add(String.format("%.2f", downPayment));
            row.add(String.format("%.1f", downPaymentPercent));
        } else {
            row.add("");
            row.add("");
        }
        
        row.add(safeGetString(transaction, "commissionAmount"));
        
        // Property details
        if (property != null) {
            row.add(safeGetString(property, "_id"));
            row.add(safeGetString(property, "propertyType"));
            row.add(safeGetString(property, "yearBuilt"));
            row.add(safeGetString(property, "bedrooms"));
            row.add(safeGetString(property, "bathrooms"));
            
            Double livingArea = safeGetDouble(property, "livingArea");
            row.add(livingArea != null ? String.format("%.0f", livingArea) : "");
            row.add(safeGetString(property, "lotSize"));
            
            // Price per sqft
            if (salePrice != null && livingArea != null && livingArea > 0) {
                row.add(String.format("%.2f", salePrice / livingArea));
            } else {
                row.add("");
            }
            
            // Address information
            Document address = (Document) property.get("address");
            if (address != null) {
                row.add(safeGetString(address, "fullAddress"));
                row.add(safeGetString(address, "streetNumber"));
                row.add(safeGetString(address, "streetName"));
                row.add(safeGetString(address, "unitNumber"));
                row.add(safeGetString(address, "city"));
                row.add(safeGetString(address, "state"));
                row.add(safeGetString(address, "zipcode"));
                row.add(safeGetString(address, "county"));
                row.add(safeGetString(address, "neighborhood"));
                row.add(safeGetString(address, "subdivision"));
                
                Document location = (Document) address.get("location");
                if (location != null) {
                    row.add(safeGetString(location, "lat"));
                    row.add(safeGetString(location, "lng"));
                } else {
                    row.add("");
                    row.add("");
                }
            } else {
                for (int i = 0; i < 12; i++) row.add("");
            }
            
            // Property features
            Document features = (Document) property.get("features");
            if (features != null) {
                row.add(features.getBoolean("hasPool", false) ? "true" : "false");
                row.add(features.getBoolean("hasSpa", false) ? "true" : "false");
                row.add(features.getBoolean("hasFireplace", false) ? "true" : "false");
                row.add(features.getBoolean("hasBasement", false) ? "true" : "false");
                row.add(features.getBoolean("hasGarage", false) ? "true" : "false");
                row.add(safeGetString(features, "garageSpaces"));
                row.add(features.getBoolean("hasView", false) ? "true" : "false");
                row.add(features.getBoolean("hasWaterfront", false) ? "true" : "false");
                row.add(safeGetString(property, "stories"));
                row.add(safeGetString(property, "constructionType"));
                row.add(safeGetString(property, "roofType"));
                row.add(safeGetString(property, "heatingType"));
                row.add(safeGetString(property, "coolingType"));
                row.add(safeGetString(property, "flooringType"));
            } else {
                for (int i = 0; i < 14; i++) row.add("");
            }
        } else {
            // Add empty fields for all property columns
            for (int i = 0; i < 34; i++) row.add("");
        }
        
        // Listing information
        if (listing != null) {
            row.add(safeGetString(listing, "_id"));
            row.add(safeGetString(listing, "mlsNumber"));
            
            Date listingDate = (Date) listing.get("listDate");
            row.add(listingDate != null ? listingDate.toString() : "");
            
            row.add(safeGetString(listing, "originalPrice"));
            row.add(safeGetString(listing, "price"));
            
            Object priceHistory = listing.get("priceHistory");
            if (priceHistory instanceof List) {
                row.add(String.valueOf(((List<?>) priceHistory).size()));
            } else {
                row.add("0");
            }
            
            row.add(safeGetString(listing, "status"));
            row.add(safeGetString(listing, "listingType"));
            row.add(safeGetString(listing, "listingAgreement"));
        } else {
            for (int i = 0; i < 9; i++) row.add("");
        }
        
        // Buyer information
        List<?> buyers = (List<?>) transaction.get("buyers");
        if (buyers != null && !buyers.isEmpty()) {
            row.add(String.valueOf(buyers.size()));
            
            // First buyer
            if (buyers.get(0) instanceof ObjectId) {
                ObjectId buyerId = (ObjectId) buyers.get(0);
                Document buyer = peopleMap.get(buyerId);
                if (buyer != null) {
                    row.add(safeGetString(buyer, "_id"));
                    row.add(safeGetString(buyer, "name"));
                    row.add(safeGetString(buyer, "email"));
                    row.add(safeGetString(buyer, "phoneNumber"));
                    
                    Document buyerAddress = (Document) buyer.get("address");
                    if (buyerAddress != null) {
                        row.add(safeGetString(buyerAddress, "fullAddress"));
                    } else {
                        row.add("");
                    }
                } else {
                    for (int i = 0; i < 5; i++) row.add("");
                }
            } else {
                for (int i = 0; i < 5; i++) row.add("");
            }
            
            // Second buyer
            if (buyers.size() > 1 && buyers.get(1) instanceof ObjectId) {
                ObjectId buyer2Id = (ObjectId) buyers.get(1);
                Document buyer2 = peopleMap.get(buyer2Id);
                if (buyer2 != null) {
                    row.add(safeGetString(buyer2, "_id"));
                    row.add(safeGetString(buyer2, "name"));
                } else {
                    row.add("");
                    row.add("");
                }
            } else {
                row.add("");
                row.add("");
            }
            
            row.add(""); // buyer type placeholder
        } else {
            for (int i = 0; i < 9; i++) row.add("");
        }
        
        // Seller information - similar structure to buyers
        List<?> sellers = (List<?>) transaction.get("sellers");
        if (sellers != null && !sellers.isEmpty()) {
            row.add(String.valueOf(sellers.size()));
            
            // Process sellers similar to buyers...
            // For brevity, adding placeholders
            for (int i = 0; i < 8; i++) row.add("");
        } else {
            for (int i = 0; i < 9; i++) row.add("");
        }
        
        // Agent information
        processAgentInformation(row, transaction, listing);
        
        // Transaction details - placeholders for now
        for (int i = 0; i < 10; i++) row.add("");
        
        // Market conditions - placeholders
        for (int i = 0; i < 6; i++) row.add("");
        
        // Financing type indicators
        String financingType = safeGetString(transaction, "financingType").toLowerCase();
        for (String financing : FINANCING_TYPES) {
            row.add(financingType.contains(financing) ? "true" : "false");
        }
        
        // Property type indicators
        String propertyType = property != null ? safeGetString(property, "propertyType").toLowerCase() : "";
        for (String propType : PROPERTY_TYPES) {
            row.add(propertyType.contains(propType.replace("_", " ")) ? "true" : "false");
        }
        
        // Transaction Derived Metrics
        ObjectId transactionId = transaction.getObjectId("_id");
        Document derivedData = transactionsDerivedMap.get(transactionId);
        if (derivedData != null) {
            row.add(safeGetString(derivedData, "performanceScore"));
            row.add(safeGetString(derivedData, "priceNegotiationPercentage"));
            row.add(safeGetString(derivedData, "marketAdjustedSalePrice"));
            row.add(safeGetString(derivedData, "complexityScore"));
            row.add(safeGetString(derivedData, "closingSpeedScore"));
        } else {
            for (int i = 0; i < 5; i++) row.add("");
        }
        
        // Client History - Buyer
        // buyers already defined above
        List<Document> buyerClients = null;
        if (buyers != null && !buyers.isEmpty() && buyers.get(0) instanceof ObjectId) {
            ObjectId buyerId = (ObjectId) buyers.get(0);
            buyerClients = agentClientsByPersonMap.get(buyerId);
            if (buyerClients != null && !buyerClients.isEmpty()) {
                // Count previous purchases
                long previousPurchases = buyerClients.stream()
                    .filter(c -> "purchase".equals(c.getString("transactionType")))
                    .count();
                row.add(String.valueOf(previousPurchases));
                
                // Get earliest client date
                Date earliestDate = buyerClients.stream()
                    .map(c -> (Date) c.get("createdDate"))
                    .filter(Objects::nonNull)
                    .min(Date::compareTo)
                    .orElse(null);
                row.add(earliestDate != null ? earliestDate.toString() : "");
                
                // Calculate total spent
                double totalSpent = buyerClients.stream()
                    .mapToDouble(c -> {
                        Object amount = c.get("transactionAmount");
                        return amount instanceof Number ? ((Number) amount).doubleValue() : 0.0;
                    })
                    .sum();
                row.add(String.format("%.2f", totalSpent));
            } else {
                row.add("0"); // previous purchases
                row.add(""); // client since
                row.add("0.00"); // total spent
            }
        } else {
            for (int i = 0; i < 3; i++) row.add("");
        }
        
        // Client History - Seller
        // sellers already defined above
        List<Document> sellerClients = null;
        if (sellers != null && !sellers.isEmpty() && sellers.get(0) instanceof ObjectId) {
            ObjectId sellerId = (ObjectId) sellers.get(0);
            sellerClients = agentClientsByPersonMap.get(sellerId);
            if (sellerClients != null && !sellerClients.isEmpty()) {
                // Count previous sales
                long previousSales = sellerClients.stream()
                    .filter(c -> "sale".equals(c.getString("transactionType")))
                    .count();
                row.add(String.valueOf(previousSales));
                
                // Get earliest client date
                Date earliestDate = sellerClients.stream()
                    .map(c -> (Date) c.get("createdDate"))
                    .filter(Objects::nonNull)
                    .min(Date::compareTo)
                    .orElse(null);
                row.add(earliestDate != null ? earliestDate.toString() : "");
            } else {
                row.add("0"); // previous sales
                row.add(""); // client since
            }
            
            // Is repeat client transaction
            boolean isRepeat = (buyerClients != null && !buyerClients.isEmpty()) || 
                               (sellerClients != null && !sellerClients.isEmpty());
            row.add(isRepeat ? "true" : "false");
            
            // Client loyalty score (simple calculation based on history)
            int loyaltyScore = 0;
            if (buyerClients != null) loyaltyScore += buyerClients.size();
            if (sellerClients != null) loyaltyScore += sellerClients.size();
            row.add(String.valueOf(loyaltyScore));
        } else {
            for (int i = 0; i < 4; i++) row.add("");
        }
        
        // Residence Portfolio
        if (buyers != null && !buyers.isEmpty() && buyers.get(0) instanceof ObjectId) {
            ObjectId buyerId = (ObjectId) buyers.get(0);
            List<Document> buyerResidences = residencesByPersonMap.get(buyerId);
            if (buyerResidences != null) {
                row.add(String.valueOf(buyerResidences.size())); // total properties owned
                
                // Calculate portfolio value
                double portfolioValue = buyerResidences.stream()
                    .mapToDouble(r -> {
                        Object value = r.get("estimatedValue");
                        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
                    })
                    .sum();
                row.add(String.format("%.2f", portfolioValue));
            } else {
                row.add("0"); // properties owned
                row.add("0.00"); // portfolio value
            }
        } else {
            row.add("");
            row.add("");
        }
        
        // Seller remaining properties and analysis
        if (sellers != null && !sellers.isEmpty() && sellers.get(0) instanceof ObjectId) {
            ObjectId sellerId = (ObjectId) sellers.get(0);
            List<Document> sellerResidences = residencesByPersonMap.get(sellerId);
            if (sellerResidences != null) {
                // Subtract 1 for the property being sold
                int remaining = Math.max(0, sellerResidences.size() - 1);
                row.add(String.valueOf(remaining));
                
                // Get next residence type if any
                Document nextResidence = sellerResidences.stream()
                    .filter(r -> !r.getObjectId("_id").equals(property != null ? property.getObjectId("_id") : null))
                    .findFirst()
                    .orElse(null);
                row.add(nextResidence != null ? safeGetString(nextResidence, "propertyType") : "");
                
                // Is investment property (seller has multiple properties)
                row.add(sellerResidences.size() > 1 ? "true" : "false");
            } else {
                row.add("0"); // remaining properties
                row.add(""); // next residence type
                row.add("false"); // is investment
            }
        } else {
            row.add("");
            row.add("");
            row.add("");
        }
        
        // Downsizing/Upsizing analysis
        if (property != null && listing != null) {
            Double currentSize = safeGetDouble(property, "livingArea");
            // salePrice already defined above
            
            // Simple heuristic: if they're buying, compare sale price to determine up/down sizing
            boolean isDownsizing = false;
            boolean isUpsizing = false;
            
            if (currentSize != null && salePrice != null) {
                // This is simplified - in reality would need buyer's next property
                Double avgPricePerSqft = salePrice / currentSize;
                // Placeholder logic
                isDownsizing = false;
                isUpsizing = false;
            }
            
            row.add(isDownsizing ? "true" : "false");
            row.add(isUpsizing ? "true" : "false");
        } else {
            row.add("false"); // downsizing
            row.add("false"); // upsizing
        }
        
        return row.toArray(new String[0]);
    }
    
    private void processAgentInformation(List<String> row, Document transaction, Document listing) {
        // Listing agent from listing document
        if (listing != null) {
            Object listingAgentRef = listing.get("listingAgentId");
            if (listingAgentRef instanceof ObjectId) {
                Document listingAgent = agentsMap.get((ObjectId) listingAgentRef);
                if (listingAgent != null) {
                    row.add(safeGetString(listingAgent, "_id"));
                    row.add(safeGetString(listingAgent, "fullName"));
                    
                    Document realmData = (Document) listingAgent.get("realmData");
                    if (realmData != null) {
                        row.add(safeGetString(realmData, "email"));
                        row.add(safeGetString(realmData, "phone"));
                        row.add(safeGetString(realmData, "licenseNumber"));
                        
                        Date startDate = (Date) realmData.get("professionalStartDate");
                        if (startDate != null) {
                            long years = (System.currentTimeMillis() - startDate.getTime()) / (1000L * 60 * 60 * 24 * 365);
                            row.add(String.valueOf(years));
                        } else {
                            row.add("");
                        }
                    } else {
                        for (int i = 0; i < 4; i++) row.add("");
                    }
                    
                    // Listing brokerage
                    addBrokerageInfo(row, listingAgent);
                    row.add(""); // commission placeholder
                } else {
                    for (int i = 0; i < 9; i++) row.add("");
                }
            } else {
                for (int i = 0; i < 9; i++) row.add("");
            }
        } else {
            for (int i = 0; i < 9; i++) row.add("");
        }
        
        // Buyer agent from transaction
        List<?> sellingAgents = (List<?>) transaction.get("sellingAgents");
        if (sellingAgents != null && !sellingAgents.isEmpty()) {
            Object buyerAgentRef = sellingAgents.get(0);
            if (buyerAgentRef instanceof ObjectId) {
                Document buyerAgent = agentsMap.get((ObjectId) buyerAgentRef);
                if (buyerAgent != null) {
                    row.add(safeGetString(buyerAgent, "_id"));
                    row.add(safeGetString(buyerAgent, "fullName"));
                    
                    Document realmData = (Document) buyerAgent.get("realmData");
                    if (realmData != null) {
                        row.add(safeGetString(realmData, "email"));
                        row.add(safeGetString(realmData, "phone"));
                        row.add(safeGetString(realmData, "licenseNumber"));
                        
                        Date startDate = (Date) realmData.get("professionalStartDate");
                        if (startDate != null) {
                            long years = (System.currentTimeMillis() - startDate.getTime()) / (1000L * 60 * 60 * 24 * 365);
                            row.add(String.valueOf(years));
                        } else {
                            row.add("");
                        }
                    } else {
                        for (int i = 0; i < 4; i++) row.add("");
                    }
                    
                    // Buyer brokerage
                    addBrokerageInfo(row, buyerAgent);
                    row.add(""); // commission placeholder
                } else {
                    for (int i = 0; i < 9; i++) row.add("");
                }
            } else {
                for (int i = 0; i < 9; i++) row.add("");
            }
        } else {
            for (int i = 0; i < 9; i++) row.add("");
        }
    }
    
    private void addBrokerageInfo(List<String> row, Document agent) {
        Document realmData = (Document) agent.get("realmData");
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
                            Document brokerage = brokeragesMap.get((ObjectId) brokerageRef);
                            if (brokerage != null) {
                                row.add(safeGetString(brokerage, "_id"));
                                row.add(safeGetString(brokerage, "name"));
                                return;
                            }
                        }
                    } else if (firstBrokerage instanceof ObjectId) {
                        Document brokerage = brokeragesMap.get((ObjectId) firstBrokerage);
                        if (brokerage != null) {
                            row.add(safeGetString(brokerage, "_id"));
                            row.add(safeGetString(brokerage, "name"));
                            return;
                        }
                    }
                }
            }
        }
        row.add("");
        row.add("");
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
}