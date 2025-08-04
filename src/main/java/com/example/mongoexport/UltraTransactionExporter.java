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
import java.util.stream.Collectors;

/**
 * Ultra-comprehensive transaction history exporter with complete deal details
 */
public class UltraTransactionExporter {
    private static final Logger logger = LoggerFactory.getLogger(UltraTransactionExporter.class);
    private static final int BATCH_SIZE = 2000;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    
    private final ExportConfig config;
    private final MongoDatabase database;
    
    // In-memory lookup maps
    private Map<ObjectId, Document> agentsMap = new HashMap<>();
    private Map<ObjectId, Document> peopleMap = new HashMap<>();
    private Map<ObjectId, Document> brokeragesMap = new HashMap<>();
    
    // Additional collections for enhanced attributes
    private Map<ObjectId, List<Document>> agentClientsByPersonMap = new HashMap<>();
    private Map<ObjectId, List<Document>> residencesByPersonMap = new HashMap<>();
    
    // Column statistics tracking
    private Map<Integer, Map<String, Integer>> columnStats = new HashMap<>();
    private Map<Integer, Integer> nullCounts = new HashMap<>();
    private int totalRows = 0;
    private Map<ObjectId, Document> transactionsDerivedMap = new HashMap<>();
    
    public UltraTransactionExporter() {
        this.config = new ExportConfig();
        MongoClient mongoClient = MongoClients.create(config.getMongoUrl());
        this.database = mongoClient.getDatabase(config.getDatabaseName());
    }
    
    public static void main(String[] args) {
        UltraTransactionExporter exporter = new UltraTransactionExporter();
        
        logger.info("Starting ultra-comprehensive transaction export...");
        
        MongoCollection<Document> transactions = exporter.database.getCollection("transactions");
        long totalTransactions = transactions.countDocuments();
        logger.info("Total transactions in database: {}", totalTransactions);
        
        exporter.exportTransactionHistoryUltraComprehensive();
        logger.info("Ultra-comprehensive transaction export completed!");
    }
    
    private void loadCollectionsIntoMemory() {
        logger.info("Loading collections into memory for transaction history analysis...");
        
        // Load agents
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
        
        // Load people
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
        
        // Load agent clients grouped by person
        logger.info("Loading agent clients collection...");
        MongoCollection<Document> agentClients = database.getCollection("agentclients");
        for (Document doc : agentClients.find()) {
            Object personRef = doc.get("person");
            if (personRef instanceof ObjectId) {
                ObjectId personId = (ObjectId) personRef;
                agentClientsByPersonMap.computeIfAbsent(personId, k -> new ArrayList<>()).add(doc);
            }
        }
        logger.info("Loaded agent clients for {} people", agentClientsByPersonMap.size());
        
        // Load residences grouped by person
        logger.info("Loading residences collection...");
        MongoCollection<Document> residences = database.getCollection("residences");
        for (Document doc : residences.find()) {
            Object personRef = doc.get("person");
            if (personRef instanceof ObjectId) {
                ObjectId personId = (ObjectId) personRef;
                residencesByPersonMap.computeIfAbsent(personId, k -> new ArrayList<>()).add(doc);
            }
        }
        logger.info("Loaded residences for {} people", residencesByPersonMap.size());
        
        // Load transactions derived
        logger.info("Loading transactions derived collection...");
        MongoCollection<Document> transactionsDerived = database.getCollection("transactionsderived");
        for (Document doc : transactionsDerived.find()) {
            ObjectId id = doc.getObjectId("_id");
            if (id != null) {
                transactionsDerivedMap.put(id, doc);
            }
        }
        logger.info("Loaded {} transactions derived into memory", transactionsDerivedMap.size());
        
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
        String outputPath = config.getOutputDirectory() + "/transaction_history_ultra_comprehensive_" + timestamp + ".csv";
        String statsPath = config.getOutputDirectory() + "/transaction_history_ultra_comprehensive_" + timestamp + "_stats.txt";
        
        try (FileWriter fileWriter = new FileWriter(outputPath); 
             CSVWriter csvWriter = new CSVWriter(fileWriter)) {
            
            // Write comprehensive headers
            String[] headers = buildComprehensiveHeaders();
            csvWriter.writeNext(headers);
            
            // Initialize column statistics
            for (int i = 0; i < headers.length; i++) {
                columnStats.put(i, new HashMap<>());
                nullCounts.put(i, 0);
            }
            
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
                        
                        // Batch fetch listings
                        Map<ObjectId, Document> batchListings = new HashMap<>();
                        if (!listingIds.isEmpty()) {
                            listings.find(new Document("_id", new Document("$in", new ArrayList<>(listingIds))))
                                    .forEach(doc -> {
                                        ObjectId id = doc.getObjectId("_id");
                                        if (id != null) batchListings.put(id, doc);
                                    });
                        }
                        
                        // Collect property IDs from listings too
                        for (Document listing : batchListings.values()) {
                            Object propertyRef = listing.get("property");
                            if (propertyRef instanceof ObjectId) {
                                propertyIds.add((ObjectId) propertyRef);
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
                        
                        // Process batch
                        for (Document transaction : batch) {
                            Document listing = null;
                            Object listingRef = transaction.get("listing");
                            if (listingRef instanceof ObjectId) {
                                listing = batchListings.get((ObjectId) listingRef);
                            }
                            
                            Document property = null;
                            Object propertyRef = transaction.get("property");
                            if (propertyRef instanceof ObjectId) {
                                property = batchProperties.get((ObjectId) propertyRef);
                            } else if (listing != null) {
                                propertyRef = listing.get("property");
                                if (propertyRef instanceof ObjectId) {
                                    property = batchProperties.get((ObjectId) propertyRef);
                                }
                            }
                            
                            // Build row with all data
                            String[] row = buildComprehensiveRow(transaction, listing, property);
                            csvWriter.writeNext(row);
                            
                            // Collect statistics for this row
                            collectRowStatistics(row);
                            
                            totalCount++;
                            totalRows++;
                        }
                        
                        // Log progress
                        if (totalCount % 5000 == 0) {
                            long currentTime = System.currentTimeMillis();
                            double secondsElapsed = (currentTime - startTime) / 1000.0;
                            double rate = totalCount / secondsElapsed;
                            logger.info("Processed {} transactions... ({} transactions/sec)", 
                                totalCount, String.format("%.1f", rate));
                        }
                        
                        // Clear batch
                        batch.clear();
                    }
                }
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            double totalSeconds = totalTime / 1000.0;
            logger.info("Export completed: {} total transactions written to {}", totalCount, outputPath);
            logger.info("Total time: {} seconds ({} transactions/sec)", 
                String.format("%.1f", totalSeconds), String.format("%.1f", totalCount / totalSeconds));
            
            // Write statistics file
            writeStatisticsFile(statsPath, headers);
            
        } catch (IOException e) {
            logger.error("Failed to export transactions", e);
        }
    }
    
    private String[] buildComprehensiveHeaders() {
        List<String> headers = new ArrayList<>();
        
        // Transaction core fields
        headers.addAll(Arrays.asList("Closing Date", "Sale Price", "List Price",
            "Price Difference", "Price Ratio (%)", "Days on Market", "Financing Type", "Loan Amount",
            "Loan Type", "Down Payment", "Down Payment (%)", "Commission Amount"));
            
        // Property details
        headers.addAll(Arrays.asList("Property Type", "Year Built", "Bedrooms",
            "Bathrooms", "Living Area (sq ft)", "Lot Size (sq ft)", "Price per Sq Ft", "Full Address", "Street Number",
            "Street Name", "Unit Number", "City", "State", "ZIP Code", "County", "Neighborhood",
            "Subdivision", "Latitude", "Longitude"));
            
        // Property features
        headers.addAll(Arrays.asList("Garage Spaces", "Stories", "Construction Type",
            "Roof Type", "Heating Type", "Cooling Type", "Flooring Type"));
            
        // Listing information
        headers.addAll(Arrays.asList("MLS Number", "Listing Date", "Original List Price",
            "Final List Price", "Price Reductions", "Listing Status", "Listing Type", "Listing Agreement"));
            
        // Buyer information
        headers.addAll(Arrays.asList("Buyer Count", "Buyer 1 Name", "Buyer 1 Email",
            "Buyer 1 Phone", "Buyer 1 Address", "Buyer 2 Name", "Buyer Type"));
            
        // Seller information
        headers.addAll(Arrays.asList("Seller Count", "Seller 1 Name", "Seller 1 Email",
            "Seller 1 Phone", "Seller 1 Address", "Seller 2 Name", "Seller Type"));
            
        // Listing agent information
        headers.addAll(Arrays.asList("Listing Agent Name", "Listing Agent Email",
            "Listing Agent Phone", "Listing Agent License #", "Listing Agent Years Experience",
            "Listing Brokerage Name", "Listing Commission"));
            
        // Buyer agent information
        headers.addAll(Arrays.asList("Buyer Agent Name", "Buyer Agent Email",
            "Buyer Agent Phone", "Buyer Agent License #", "Buyer Agent Years Experience",
            "Buyer Brokerage Name", "Buyer Commission"));
            
        // Market conditions
        headers.addAll(Arrays.asList("Market Temperature", "Comparable Sales Avg", "Neighborhood Trend",
            "School Ratings", "Walk Score", "Crime Index"));
            
        // Derived fields
        headers.addAll(Arrays.asList("Season", "Weekday", "Buyer Representation", "Dual Agency",
            "First Time Buyer", "Cash Purchase", "Investment Property"));
            
        // Client relationship data
        headers.addAll(Arrays.asList("Buyer Previous Purchases", "Buyer-Agent Relationship Length",
            "Seller Previous Sales", "Seller-Agent Relationship Length"));
            
        // Residence history
        headers.addAll(Arrays.asList("Buyer Residence Count", "Buyer Avg Residence Duration",
            "Seller Residence Count", "Seller Next Residence"));
            
        // Transaction derived data
        headers.addAll(Arrays.asList("Price Negotiation (%)", "Closing Speed Rating",
            "Transaction Complexity Score", "Agent Effectiveness Score"));
        
        return headers.toArray(new String[0]);
    }
    
    private String[] buildComprehensiveRow(Document transaction, Document listing, Document property) {
        List<String> row = new ArrayList<>();
        
        // Transaction core fields
        Date closingDate = (Date) transaction.get("closingDate");
        row.add(closingDate != null ? DATE_FORMAT.format(closingDate) : "");
        
        Double salePrice = safeGetDouble(transaction, "price");
        row.add(salePrice != null ? String.format("%.2f", salePrice) : "");
        
        Double listPrice = null;
        if (listing != null) {
            Object priceObj = listing.get("price");
            if (priceObj instanceof Document) {
                listPrice = safeGetDouble((Document) priceObj, "amount");
            } else if (priceObj instanceof Number) {
                listPrice = ((Number) priceObj).doubleValue();
            }
        }
        row.add(listPrice != null ? String.format("%.2f", listPrice) : "");
        
        // Price difference and ratio
        if (salePrice != null && listPrice != null && listPrice > 0) {
            row.add(String.format("%.2f", salePrice - listPrice));
            row.add(String.format("%.2f", (salePrice / listPrice) * 100));
        } else {
            row.add("");
            row.add("");
        }
        
        // Days on market
        if (listing != null && closingDate != null) {
            Date listDate = (Date) listing.get("dateListed");
            if (listDate != null) {
                long days = (closingDate.getTime() - listDate.getTime()) / (1000 * 60 * 60 * 24);
                row.add(String.valueOf(days));
            } else {
                row.add("");
            }
        } else {
            row.add("");
        }
        
        row.add(safeGetString(transaction, "financingType"));
        row.add(safeGetString(transaction, "loanAmount"));
        row.add(safeGetString(transaction, "loanType"));
        row.add(safeGetString(transaction, "downPayment"));
        row.add(safeGetString(transaction, "downPaymentPercent"));
        row.add(safeGetString(transaction, "commissionAmount"));
        
        // Property details
        if (property != null) {
            row.add(safeGetString(property, "propertyType"));
            row.add(safeGetString(property, "yearBuilt"));
            row.add(safeGetString(property, "bedrooms"));
            row.add(safeGetString(property, "bathrooms"));
            row.add(safeGetString(property, "livingArea"));
            row.add(safeGetString(property, "lotSize"));
            
            Double livingArea = safeGetDouble(property, "livingArea");
            if (livingArea != null && livingArea > 0 && salePrice != null) {
                row.add(String.format("%.2f", salePrice / livingArea));
            } else {
                row.add("");
            }
            
            // Address fields
            Document address = (Document) property.get("address");
            if (address != null) {
                row.add(safeGetString(address, "fullAddress"));
                row.add(safeGetString(address, "streetNumber"));
                row.add(safeGetString(address, "streetName"));
                row.add(safeGetString(address, "unitNumber"));
                row.add(safeGetString(address, "city"));
                row.add(safeGetString(address, "state"));
                row.add(safeGetString(address, "zip"));
                row.add(safeGetString(address, "county"));
                row.add(safeGetString(address, "neighborhood"));
                row.add(safeGetString(address, "subdivision"));
                
                Document location = (Document) address.get("location");
                if (location != null) {
                    row.add(safeGetString(location, "latitude"));
                    row.add(safeGetString(location, "longitude"));
                } else {
                    row.add("");
                    row.add("");
                }
            } else {
                for (int i = 0; i < 12; i++) row.add("");
            }
            
            // Property features (only meaningful ones)
            row.add(safeGetString(property, "garageSpaces"));
            row.add(safeGetString(property, "stories"));
            row.add(safeGetString(property, "constructionType"));
            row.add(safeGetString(property, "roofType"));
            row.add(safeGetString(property, "heatingType"));
            row.add(safeGetString(property, "coolingType"));
            row.add(safeGetString(property, "flooringType"));
        } else {
            for (int i = 0; i < 26; i++) row.add("");
        }
        
        // Listing information
        if (listing != null) {
            row.add(safeGetString(listing, "mlsNumber"));
            
            Date listDate = (Date) listing.get("dateListed");
            row.add(listDate != null ? DATE_FORMAT.format(listDate) : "");
            
            row.add(safeGetString(listing, "originalListPrice"));
            row.add(String.valueOf(listPrice != null ? listPrice : ""));
            
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
            for (int i = 0; i < 8; i++) row.add("");
        }
        
        // Buyer information
        Object buyersObj = transaction.get("buyers");
        if (buyersObj instanceof List) {
            List<?> buyers = (List<?>) buyersObj;
            row.add(String.valueOf(buyers.size()));
            
            if (!buyers.isEmpty()) {
                Object firstBuyer = buyers.get(0);
                if (firstBuyer instanceof ObjectId) {
                    Document buyer = peopleMap.get((ObjectId) firstBuyer);
                    if (buyer != null) {
                        row.add(safeGetString(buyer, "fullName"));
                        row.add(safeGetString(buyer, "email"));
                        row.add(safeGetString(buyer, "phone"));
                        Document buyerAddress = (Document) buyer.get("address");
                        if (buyerAddress != null) {
                            row.add(safeGetString(buyerAddress, "fullAddress"));
                        } else {
                            row.add("");
                        }
                    } else {
                        for (int i = 0; i < 4; i++) row.add("");
                    }
                } else {
                    for (int i = 0; i < 4; i++) row.add("");
                }
                
                if (buyers.size() > 1) {
                    Object secondBuyer = buyers.get(1);
                    if (secondBuyer instanceof ObjectId) {
                        Document buyer2 = peopleMap.get((ObjectId) secondBuyer);
                        row.add(buyer2 != null ? safeGetString(buyer2, "fullName") : "");
                    } else {
                        row.add("");
                    }
                } else {
                    row.add("");
                }
            } else {
                for (int i = 0; i < 5; i++) row.add("");
            }
            
            row.add(safeGetString(transaction, "buyerType"));
        } else {
            for (int i = 0; i < 7; i++) row.add("");
        }
        
        // Seller information
        Object sellersObj = transaction.get("sellers");
        if (sellersObj instanceof List) {
            List<?> sellers = (List<?>) sellersObj;
            row.add(String.valueOf(sellers.size()));
            
            if (!sellers.isEmpty()) {
                Object firstSeller = sellers.get(0);
                if (firstSeller instanceof ObjectId) {
                    Document seller = peopleMap.get((ObjectId) firstSeller);
                    if (seller != null) {
                        row.add(safeGetString(seller, "fullName"));
                        row.add(safeGetString(seller, "email"));
                        row.add(safeGetString(seller, "phone"));
                        Document sellerAddress = (Document) seller.get("address");
                        if (sellerAddress != null) {
                            row.add(safeGetString(sellerAddress, "fullAddress"));
                        } else {
                            row.add("");
                        }
                    } else {
                        for (int i = 0; i < 4; i++) row.add("");
                    }
                } else {
                    for (int i = 0; i < 4; i++) row.add("");
                }
                
                if (sellers.size() > 1) {
                    Object secondSeller = sellers.get(1);
                    if (secondSeller instanceof ObjectId) {
                        Document seller2 = peopleMap.get((ObjectId) secondSeller);
                        row.add(seller2 != null ? safeGetString(seller2, "fullName") : "");
                    } else {
                        row.add("");
                    }
                } else {
                    row.add("");
                }
            } else {
                for (int i = 0; i < 5; i++) row.add("");
            }
            
            row.add(safeGetString(transaction, "sellerType"));
        } else {
            for (int i = 0; i < 7; i++) row.add("");
        }
        
        // Listing agent information
        if (listing != null) {
            Object listingAgentRef = listing.get("listingAgentId");
            if (listingAgentRef instanceof ObjectId) {
                Document listingAgent = agentsMap.get((ObjectId) listingAgentRef);
                if (listingAgent != null) {
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
                        
                        // Get brokerage name
                        Object brokeragesObj = realmData.get("brokerages");
                        if (brokeragesObj instanceof List && !((List<?>) brokeragesObj).isEmpty()) {
                            Object firstBrokerage = ((List<?>) brokeragesObj).get(0);
                            ObjectId brokerageId = null;
                            if (firstBrokerage instanceof Document) {
                                brokerageId = ((Document) firstBrokerage).getObjectId("_id");
                            } else if (firstBrokerage instanceof ObjectId) {
                                brokerageId = (ObjectId) firstBrokerage;
                            }
                            
                            if (brokerageId != null) {
                                Document brokerage = brokeragesMap.get(brokerageId);
                                row.add(brokerage != null ? safeGetString(brokerage, "name") : "");
                            } else {
                                row.add("");
                            }
                        } else {
                            row.add("");
                        }
                    } else {
                        for (int i = 0; i < 5; i++) row.add("");
                    }
                    
                    row.add(safeGetString(transaction, "listingCommission"));
                } else {
                    for (int i = 0; i < 7; i++) row.add("");
                }
            } else {
                for (int i = 0; i < 7; i++) row.add("");
            }
        } else {
            for (int i = 0; i < 7; i++) row.add("");
        }
        
        // Buyer agent information
        Object buyerAgentRef = transaction.get("buyerAgent");
        if (buyerAgentRef instanceof ObjectId) {
            Document buyerAgent = agentsMap.get((ObjectId) buyerAgentRef);
            if (buyerAgent != null) {
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
                    
                    // Get brokerage name
                    Object brokeragesObj = realmData.get("brokerages");
                    if (brokeragesObj instanceof List && !((List<?>) brokeragesObj).isEmpty()) {
                        Object firstBrokerage = ((List<?>) brokeragesObj).get(0);
                        ObjectId brokerageId = null;
                        if (firstBrokerage instanceof Document) {
                            brokerageId = ((Document) firstBrokerage).getObjectId("_id");
                        } else if (firstBrokerage instanceof ObjectId) {
                            brokerageId = (ObjectId) firstBrokerage;
                        }
                        
                        if (brokerageId != null) {
                            Document brokerage = brokeragesMap.get(brokerageId);
                            row.add(brokerage != null ? safeGetString(brokerage, "name") : "");
                        } else {
                            row.add("");
                        }
                    } else {
                        row.add("");
                    }
                } else {
                    for (int i = 0; i < 5; i++) row.add("");
                }
                
                row.add(safeGetString(transaction, "buyerCommission"));
            } else {
                for (int i = 0; i < 7; i++) row.add("");
            }
        } else {
            for (int i = 0; i < 7; i++) row.add("");
        }
        
        // Market conditions (placeholder - would need additional data sources)
        for (int i = 0; i < 6; i++) row.add("");
        
        // Derived fields
        if (closingDate != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(closingDate);
            int month = cal.get(Calendar.MONTH);
            String season = month >= 2 && month <= 4 ? "Spring" :
                           month >= 5 && month <= 7 ? "Summer" :
                           month >= 8 && month <= 10 ? "Fall" : "Winter";
            row.add(season);
            
            String[] weekdays = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            row.add(weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]);
        } else {
            row.add("");
            row.add("");
        }
        
        // Buyer representation and other flags
        row.add(buyerAgentRef != null ? "true" : "false");
        
        // Dual agency check
        boolean dualAgency = false;
        if (listing != null && buyerAgentRef != null) {
            Object listingAgentRef = listing.get("listingAgentId");
            dualAgency = buyerAgentRef.equals(listingAgentRef);
        }
        row.add(String.valueOf(dualAgency));
        
        row.add(""); // first_time_buyer - would need additional data
        row.add(safeGetString(transaction, "financingType").equalsIgnoreCase("cash") ? "true" : "false");
        row.add(""); // investment_property - would need additional data
        
        // Client relationship data
        if (buyersObj instanceof List && !((List<?>) buyersObj).isEmpty()) {
            Object firstBuyer = ((List<?>) buyersObj).get(0);
            if (firstBuyer instanceof ObjectId) {
                List<Document> buyerClients = agentClientsByPersonMap.get((ObjectId) firstBuyer);
                row.add(buyerClients != null ? String.valueOf(buyerClients.size()) : "0");
                row.add(""); // relationship length - would need date calculations
            } else {
                row.add("0");
                row.add("");
            }
        } else {
            row.add("0");
            row.add("");
        }
        
        if (sellersObj instanceof List && !((List<?>) sellersObj).isEmpty()) {
            Object firstSeller = ((List<?>) sellersObj).get(0);
            if (firstSeller instanceof ObjectId) {
                List<Document> sellerClients = agentClientsByPersonMap.get((ObjectId) firstSeller);
                row.add(sellerClients != null ? String.valueOf(sellerClients.size()) : "0");
                row.add(""); // relationship length
            } else {
                row.add("0");
                row.add("");
            }
        } else {
            row.add("0");
            row.add("");
        }
        
        // Residence history
        if (buyersObj instanceof List && !((List<?>) buyersObj).isEmpty()) {
            Object firstBuyer = ((List<?>) buyersObj).get(0);
            if (firstBuyer instanceof ObjectId) {
                List<Document> buyerResidences = residencesByPersonMap.get((ObjectId) firstBuyer);
                row.add(buyerResidences != null ? String.valueOf(buyerResidences.size()) : "0");
                row.add(""); // avg duration
            } else {
                row.add("0");
                row.add("");
            }
        } else {
            row.add("0");
            row.add("");
        }
        
        if (sellersObj instanceof List && !((List<?>) sellersObj).isEmpty()) {
            Object firstSeller = ((List<?>) sellersObj).get(0);
            if (firstSeller instanceof ObjectId) {
                List<Document> sellerResidences = residencesByPersonMap.get((ObjectId) firstSeller);
                row.add(sellerResidences != null ? String.valueOf(sellerResidences.size()) : "0");
                row.add(""); // next residence
            } else {
                row.add("0");
                row.add("");
            }
        } else {
            row.add("0");
            row.add("");
        }
        
        // Transaction derived data
        ObjectId transactionId = transaction.getObjectId("_id");
        Document derived = transactionsDerivedMap.get(transactionId);
        if (derived != null) {
            row.add(safeGetString(derived, "priceNegotiationPercent"));
            row.add(safeGetString(derived, "closingSpeedRating"));
            row.add(safeGetString(derived, "complexityScore"));
            row.add(safeGetString(derived, "agentEffectivenessScore"));
        } else {
            for (int i = 0; i < 4; i++) row.add("");
        }
        
        return row.toArray(new String[0]);
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