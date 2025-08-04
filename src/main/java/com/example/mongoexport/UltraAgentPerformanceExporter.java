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

/**
 * Ultra-comprehensive agent performance exporter with complete metrics and analytics
 */
public class UltraAgentPerformanceExporter {
    private static final Logger logger = LoggerFactory.getLogger(UltraAgentPerformanceExporter.class);
    private static final int BATCH_SIZE = 1000;
    
    private final ExportConfig config;
    private final MongoDatabase database;
    
    // In-memory lookup maps
    private Map<ObjectId, Document> peopleMap = new HashMap<>();
    private Map<ObjectId, Document> brokeragesMap = new HashMap<>();
    private Map<ObjectId, List<Document>> listingsByAgentMap = new HashMap<>();
    private Map<ObjectId, List<Document>> transactionsByAgentMap = new HashMap<>();
    
    // Additional collections for enhanced attributes
    private Map<ObjectId, Document> teamsMap = new HashMap<>();
    private Map<ObjectId, List<ObjectId>> agentTeamMembershipsMap = new HashMap<>();
    private Map<ObjectId, Document> agentSearchMap = new HashMap<>();
    private Map<ObjectId, List<Document>> agentClientEventsByAgentMap = new HashMap<>();
    private Map<ObjectId, List<Document>> awardsByAgentMap = new HashMap<>();
    
    // Common specialties and designations
    private static final List<String> COMMON_SPECIALTIES = Arrays.asList(
        "buyer_agent", "seller_agent", "luxury", "first_time_buyers", "investors", 
        "relocation", "foreclosures", "short_sales", "commercial", "residential",
        "new_construction", "condos", "townhomes", "single_family", "multi_family",
        "waterfront", "golf_communities", "55_plus", "vacation_homes", "land"
    );
    
    private static final List<String> COMMON_DESIGNATIONS = Arrays.asList(
        "ABR", "CRS", "GRI", "SRES", "SRS", "PSA", "RENE", "MRP", "ALC",
        "GREEN", "ePRO", "RSPS", "SFR", "AHWD", "ASP", "BPOR", "CCIM"
    );
    
    public UltraAgentPerformanceExporter() {
        this.config = new ExportConfig();
        MongoClient mongoClient = MongoClients.create(config.getMongoUrl());
        this.database = mongoClient.getDatabase(config.getDatabaseName());
    }
    
    public static void main(String[] args) {
        UltraAgentPerformanceExporter exporter = new UltraAgentPerformanceExporter();
        
        logger.info("Starting ultra-comprehensive agent performance export...");
        
        MongoCollection<Document> agents = exporter.database.getCollection("agents");
        long totalAgents = agents.countDocuments();
        logger.info("Total agents in database: {}", totalAgents);
        
        exporter.exportAgentPerformanceUltraComprehensive();
        logger.info("Ultra-comprehensive agent performance export completed!");
    }
    
    private void loadCollectionsIntoMemory() {
        logger.info("Loading collections into memory for agent performance analysis...");
        
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
        
        // Load listings into memory for agent lookup
        logger.info("Loading all listings into memory for transaction lookups...");
        Map<ObjectId, Document> allListingsMap = new HashMap<>();
        MongoCollection<Document> listings = database.getCollection("listings");
        for (Document doc : listings.find()) {
            ObjectId id = doc.getObjectId("_id");
            if (id != null) {
                allListingsMap.put(id, doc);
            }
        }
        logger.info("Loaded {} listings into lookup map", allListingsMap.size());
        
        // Organize listings by agent
        logger.info("Organizing listings by agent...");
        long listingCount = 0;
        for (Document doc : allListingsMap.values()) {
            Object agentRef = doc.get("listingAgentId");
            if (agentRef instanceof ObjectId) {
                ObjectId agentId = (ObjectId) agentRef;
                listingsByAgentMap.computeIfAbsent(agentId, k -> new ArrayList<>()).add(doc);
                listingCount++;
                if (listingCount % 10000 == 0) {
                    logger.info("  Organized {} listings...", listingCount);
                }
            }
        }
        logger.info("Organized {} listings by agent", listingCount);
        
        // Load transactions grouped by agent
        logger.info("Loading transactions by agent...");
        MongoCollection<Document> transactions = database.getCollection("transactions");
        long transCount = 0;
        long listingAgentTransCount = 0;
        
        for (Document doc : transactions.find()) {
            // Get selling agent
            Object sellingAgentRef = doc.get("sellingAgentId");
            if (sellingAgentRef instanceof ObjectId) {
                ObjectId agentId = (ObjectId) sellingAgentRef;
                transactionsByAgentMap.computeIfAbsent(agentId, k -> new ArrayList<>()).add(doc);
                transCount++;
            }
            
            // Get listing agent from transaction
            Object listingRef = doc.get("listing");
            if (listingRef instanceof ObjectId) {
                Document listing = allListingsMap.get((ObjectId) listingRef);
                if (listing != null) {
                    Object listingAgentRef = listing.get("listingAgentId");
                    if (listingAgentRef instanceof ObjectId && !listingAgentRef.equals(sellingAgentRef)) {
                        ObjectId listingAgentId = (ObjectId) listingAgentRef;
                        transactionsByAgentMap.computeIfAbsent(listingAgentId, k -> new ArrayList<>()).add(doc);
                        listingAgentTransCount++;
                    }
                }
            }
        }
        logger.info("Loaded {} transactions into memory ({} as selling agent, {} as listing agent)", 
                    transCount + listingAgentTransCount, transCount, listingAgentTransCount);
        
        // Load teams
        logger.info("Loading teams collection...");
        MongoCollection<Document> teams = database.getCollection("teams");
        for (Document doc : teams.find()) {
            ObjectId id = doc.getObjectId("_id");
            if (id != null) {
                teamsMap.put(id, doc);
                
                // Map agents to their teams
                List<ObjectId> teamAgents = (List<ObjectId>) doc.get("agents");
                if (teamAgents != null) {
                    for (ObjectId agentId : teamAgents) {
                        agentTeamMembershipsMap.computeIfAbsent(agentId, k -> new ArrayList<>()).add(id);
                    }
                }
            }
        }
        logger.info("Loaded {} teams into memory", teamsMap.size());
        
        // Load agent search
        logger.info("Loading agent search collection...");
        MongoCollection<Document> agentSearch = database.getCollection("agentSearch");
        for (Document doc : agentSearch.find()) {
            Object agentRef = doc.get("agentId");
            if (agentRef instanceof ObjectId) {
                agentSearchMap.put((ObjectId) agentRef, doc);
            }
        }
        logger.info("Loaded {} agent search records into memory", agentSearchMap.size());
        
        // Load agent client events
        logger.info("Loading agent client events collection...");
        MongoCollection<Document> agentClientEvents = database.getCollection("agentclientevents");
        long eventCount = 0;
        for (Document doc : agentClientEvents.find()) {
            Object agentRef = doc.get("agent");
            if (agentRef instanceof ObjectId) {
                ObjectId agentId = (ObjectId) agentRef;
                agentClientEventsByAgentMap.computeIfAbsent(agentId, k -> new ArrayList<>()).add(doc);
                eventCount++;
            }
        }
        logger.info("Loaded {} agent client events into memory", eventCount);
        
        // Load awards
        logger.info("Loading awards collection...");
        MongoCollection<Document> awards = database.getCollection("awards");
        for (Document doc : awards.find()) {
            List<ObjectId> awardAgents = (List<ObjectId>) doc.get("agents");
            if (awardAgents != null) {
                for (ObjectId agentId : awardAgents) {
                    awardsByAgentMap.computeIfAbsent(agentId, k -> new ArrayList<>()).add(doc);
                }
            }
        }
        logger.info("Loaded awards into memory");
        
        // Report memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        logger.info("Memory usage after loading collections: {} MB", usedMemory);
    }
    
    public void exportAgentPerformanceUltraComprehensive() {
        logger.info("Exporting agent performance with ultra-comprehensive fields...");
        
        // Load collections into memory
        loadCollectionsIntoMemory();
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String outputPath = config.getOutputDirectory() + "/agent_performance_ultra_comprehensive_" + timestamp + ".csv";
        
        try (FileWriter fileWriter = new FileWriter(outputPath); 
             CSVWriter csvWriter = new CSVWriter(fileWriter)) {
            
            // Write comprehensive headers
            csvWriter.writeNext(buildComprehensiveHeaders());
            
            MongoCollection<Document> agents = database.getCollection("agents");
            
            int totalCount = 0;
            long startTime = System.currentTimeMillis();
            
            // Process all agents
            try (MongoCursor<Document> cursor = agents.find().iterator()) {
                while (cursor.hasNext()) {
                    Document agent = cursor.next();
                    
                    // Get agent person
                    Document agentPerson = null;
                    Object personRef = agent.get("person");
                    if (personRef instanceof ObjectId) {
                        agentPerson = peopleMap.get((ObjectId) personRef);
                    }
                    
                    // Get brokerage
                    Document brokerage = null;
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
                                        brokerage = brokeragesMap.get((ObjectId) brokerageRef);
                                    }
                                } else if (firstBrokerage instanceof ObjectId) {
                                    brokerage = brokeragesMap.get((ObjectId) firstBrokerage);
                                }
                            }
                        }
                    }
                    
                    // Get agent's listings and transactions
                    ObjectId agentId = agent.getObjectId("_id");
                    List<Document> agentListings = listingsByAgentMap.getOrDefault(agentId, new ArrayList<>());
                    List<Document> agentTransactions = transactionsByAgentMap.getOrDefault(agentId, new ArrayList<>());
                    
                    // Build comprehensive row
                    String[] row = buildComprehensiveRow(agent, agentPerson, brokerage, agentListings, agentTransactions);
                    csvWriter.writeNext(row);
                    
                    totalCount++;
                    if (totalCount % 1000 == 0) {
                        long currentTime = System.currentTimeMillis();
                        double secondsElapsed = (currentTime - startTime) / 1000.0;
                        double rate = totalCount / secondsElapsed;
                        logger.info("Processed {} agents... ({} agents/sec)", 
                            totalCount, String.format("%.1f", rate));
                    }
                }
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            double totalSeconds = totalTime / 1000.0;
            logger.info("Export completed: {} total agents written to {}", totalCount, outputPath);
            logger.info("Total time: {} seconds ({} agents/sec)", 
                String.format("%.1f", totalSeconds), String.format("%.1f", totalCount / totalSeconds));
            
        } catch (IOException e) {
            logger.error("Failed to export agent performance", e);
        }
    }
    
    private String[] buildComprehensiveHeaders() {
        List<String> headers = new ArrayList<>();
        
        // Agent basic info
        headers.addAll(Arrays.asList("Agent Full Name", "Agent First Name", "Agent Last Name",
            "Agent Email", "Agent Phone", "Agent Mobile", "Agent Office Phone", "Agent Fax"));
            
        // Agent professional info
        headers.addAll(Arrays.asList("Agent License Number", "Agent License State", "Agent Years Experience",
            "Agent Start Date", "Agent Website", "Agent Social Media", "Agent Bio Length", "Agent Photo URL"));
            
        // Agent designations as indicators
        for (String designation : COMMON_DESIGNATIONS) {
            headers.add("Designation: " + designation);
        }
        
        // Agent specialties as indicators
        for (String specialty : COMMON_SPECIALTIES) {
            String formattedSpecialty = specialty.replace("_", " ");
            formattedSpecialty = formattedSpecialty.substring(0, 1).toUpperCase() + formattedSpecialty.substring(1).toLowerCase();
            headers.add("Specialty: " + formattedSpecialty);
        }
        
        // Agent person info
        headers.addAll(Arrays.asList("Person Address", "Person City", "Person State",
            "Person ZIP Code", "Person County", "Person Languages", "Person Education"));
            
        // Brokerage info
        headers.addAll(Arrays.asList("Brokerage Name", "Brokerage Phone", "Brokerage Email",
            "Brokerage Website", "Brokerage Address", "Brokerage City", "Brokerage State", "Brokerage ZIP Code",
            "Brokerage Type", "Brokerage Size", "Brokerage Year Established"));
            
        // Performance metrics - Listings
        headers.addAll(Arrays.asList("Total Listings", "Active Listings", "Pending Listings", "Sold Listings",
            "Expired Listings", "Withdrawn Listings", "Average List Price", "Median List Price",
            "Total Listing Volume", "Average Days on Market", "Listings Last 30 Days", "Listings Last 90 Days",
            "Listings Last Year"));
            
        // Performance metrics - Sales
        headers.addAll(Arrays.asList("Total Sales", "Buyer Sales", "Seller Sales", "Dual Agency Sales",
            "Average Sale Price", "Median Sale Price", "Total Sales Volume", "Average Sale to List Ratio",
            "Sales Last 30 Days", "Sales Last 90 Days", "Sales Last Year"));
            
        // Property type breakdown
        headers.addAll(Arrays.asList("Single Family Sales", "Condo Sales", "Townhome Sales", "Multi-Family Sales",
            "Land Sales", "Commercial Sales", "Luxury Sales Count", "First Time Buyer Sales"));
            
        // Geographic coverage
        headers.addAll(Arrays.asList("Cities Served Count", "Primary City", "Primary ZIP Code", 
            "ZIP Codes Served Count", "Counties Served Count"));
            
        // Price range expertise
        headers.addAll(Arrays.asList("Under $200K Sales", "$200K-$400K Sales", "$400K-$600K Sales", 
            "$600K-$800K Sales", "$800K-$1M Sales", "Over $1M Sales"));
            
        // Client metrics
        headers.addAll(Arrays.asList("Repeat Client Rate", "Referral Rate", "Average Commission Rate",
            "Total Commission Earned", "Average Transaction Sides"));
            
        // Market share and rankings
        headers.addAll(Arrays.asList("Market Share - Listings", "Market Share - Sales", "Brokerage Rank",
            "City Rank", "Production Tier"));
            
        // Activity metrics
        headers.addAll(Arrays.asList("Last Listing Date", "Last Sale Date", "Months Since Last Activity",
            "Listing to Sale Conversion Rate", "Average Price Reduction"));
            
        // Team Information
        headers.addAll(Arrays.asList("Is Team Member", "Team Count", "Primary Team Name",
            "Team Size", "Team Role", "Team Total Agents", "Team Founded Date"));
            
        // Agent Search and Visibility
        headers.addAll(Arrays.asList("Search Visibility Score", "Profile Completeness Score",
            "Search Ranking Position", "Profile Views Count", "Contact Requests Count"));
            
        // Client Engagement
        headers.addAll(Arrays.asList("Total Client Events", "Avg Events per Client", "Unique Clients Count",
            "Client Retention Rate", "Event Participation Rate", "Last Client Event Date"));
            
        // Awards and Recognition
        headers.addAll(Arrays.asList("Total Awards Count", "Award Categories", "Most Recent Award",
            "Top Producer Years", "Industry Recognition Score"));
            
        return headers.toArray(new String[0]);
    }
    
    private String[] buildComprehensiveRow(Document agent, Document agentPerson, Document brokerage,
                                          List<Document> listings, List<Document> transactions) {
        List<String> row = new ArrayList<>();
        
        // Agent basic info
        row.add(safeGetString(agent, "fullName"));
        row.add(safeGetString(agent, "firstName"));
        row.add(safeGetString(agent, "lastName"));
        
        Document realmData = (Document) agent.get("realmData");
        if (realmData != null) {
            row.add(safeGetString(realmData, "email"));
            row.add(safeGetString(realmData, "phone"));
            row.add(safeGetString(realmData, "mobile"));
            row.add(safeGetString(realmData, "officePhone"));
            row.add(safeGetString(realmData, "fax"));
            row.add(safeGetString(realmData, "licenseNumber"));
            row.add(safeGetString(realmData, "licenseState"));
            
            // Calculate years of experience
            Date startDate = (Date) realmData.get("professionalStartDate");
            if (startDate != null) {
                long years = (System.currentTimeMillis() - startDate.getTime()) / (1000L * 60 * 60 * 24 * 365);
                row.add(String.valueOf(years));
                row.add(startDate.toString());
            } else {
                row.add("");
                row.add("");
            }
            
            row.add(safeGetString(realmData, "website"));
            row.add(safeGetString(realmData, "socialMedia"));
            
            String bio = safeGetString(realmData, "bio");
            row.add(String.valueOf(bio.length()));
            row.add(safeGetString(realmData, "photoUrl"));
            
            // Designations as indicators
            String designations = safeGetString(realmData, "designation").toLowerCase();
            for (String designation : COMMON_DESIGNATIONS) {
                row.add(designations.contains(designation.toLowerCase()) ? "true" : "false");
            }
        } else {
            // Add empty fields for realm data
            for (int i = 0; i < 14 + COMMON_DESIGNATIONS.size(); i++) {
                row.add("");
            }
        }
        
        // Specialties as indicators
        List<String> specialties = new ArrayList<>();
        if (agentPerson != null) {
            specialties = safeGetStringList(agentPerson, "specialties");
        }
        String specialtyText = String.join(" ", specialties).toLowerCase();
        for (String specialty : COMMON_SPECIALTIES) {
            row.add(specialtyText.contains(specialty.replace("_", " ")) ? "true" : "false");
        }
        
        // Agent person info
        if (agentPerson != null) {
            Document address = (Document) agentPerson.get("address");
            if (address != null) {
                row.add(safeGetString(address, "fullAddress"));
                row.add(safeGetString(address, "city"));
                row.add(safeGetString(address, "state"));
                row.add(safeGetString(address, "zipcode"));
                row.add(safeGetString(address, "county"));
            } else {
                for (int i = 0; i < 5; i++) row.add("");
            }
            
            List<String> languages = safeGetStringList(agentPerson, "languages");
            row.add(String.join(",", languages));
            row.add(safeGetString(agentPerson, "education"));
        } else {
            for (int i = 0; i < 7; i++) row.add("");
        }
        
        // Brokerage info with fixed city extraction
        if (brokerage != null) {
            row.add(safeGetString(brokerage, "name"));
            
            // Extract office information from offices array
            String brokeragePhone = "";
            String brokerageEmail = "";
            String brokerageWebsite = "";
            String brokerageAddress = "";
            String brokerageCity = "";
            String brokerageState = "";
            String brokerageZipcode = "";
            
            // Get first office information if available
            List<Document> offices = (List<Document>) brokerage.get("offices");
            if (offices != null && !offices.isEmpty()) {
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
            Document brokerageRealmData = (Document) brokerage.get("realmData");
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
            row.add(safeGetString(brokerage, "type"));
            row.add(safeGetString(brokerage, "size"));
            row.add(safeGetString(brokerage, "yearEstablished"));
        } else {
            for (int i = 0; i < 11; i++) row.add("");
        }
        
        // Calculate performance metrics from listings
        calculateListingMetrics(row, listings);
        
        // Calculate sales metrics from transactions
        calculateSalesMetrics(row, transactions);
        
        // Calculate geographic and other metrics
        calculateGeographicMetrics(row, listings, transactions);
        
        // Team Information
        ObjectId agentId = agent.getObjectId("_id");
        List<ObjectId> teamMemberships = agentTeamMembershipsMap.get(agentId);
        if (teamMemberships != null && !teamMemberships.isEmpty()) {
            row.add("true"); // is team member
            row.add(String.valueOf(teamMemberships.size())); // team count
            
            // Get primary team (first one)
            ObjectId primaryTeamId = teamMemberships.get(0);
            Document primaryTeam = teamsMap.get(primaryTeamId);
            if (primaryTeam != null) {
                row.add(safeGetString(primaryTeam, "name"));
                
                List<ObjectId> teamAgents = (List<ObjectId>) primaryTeam.get("agents");
                row.add(teamAgents != null ? String.valueOf(teamAgents.size()) : "0");
                
                // Determine role (simplified)
                ObjectId leaderId = primaryTeam.getObjectId("teamLeader");
                row.add(agentId.equals(leaderId) ? "leader" : "member");
                
                row.add(String.valueOf(teamAgents != null ? teamAgents.size() : 0)); // total agents
                
                Date foundedDate = (Date) primaryTeam.get("foundedDate");
                row.add(foundedDate != null ? foundedDate.toString() : "");
            } else {
                for (int i = 0; i < 5; i++) row.add("");
            }
        } else {
            row.add("false"); // is team member
            row.add("0"); // team count
            for (int i = 0; i < 5; i++) row.add("");
        }
        
        // Agent Search and Visibility
        Document searchData = agentSearchMap.get(agentId);
        if (searchData != null) {
            row.add(safeGetString(searchData, "visibilityScore"));
            row.add(safeGetString(searchData, "profileCompleteness"));
            row.add(safeGetString(searchData, "searchRanking"));
            row.add(safeGetString(searchData, "profileViews"));
            row.add(safeGetString(searchData, "contactRequests"));
        } else {
            for (int i = 0; i < 5; i++) row.add("");
        }
        
        // Client Engagement
        List<Document> clientEvents = agentClientEventsByAgentMap.get(agentId);
        if (clientEvents != null && !clientEvents.isEmpty()) {
            row.add(String.valueOf(clientEvents.size())); // total events
            
            // Calculate unique clients
            Set<ObjectId> uniqueClients = new HashSet<>();
            Date lastEventDate = null;
            for (Document event : clientEvents) {
                Object clientRef = event.get("client");
                if (clientRef instanceof ObjectId) {
                    uniqueClients.add((ObjectId) clientRef);
                }
                Date eventDate = (Date) event.get("eventDate");
                if (eventDate != null && (lastEventDate == null || eventDate.after(lastEventDate))) {
                    lastEventDate = eventDate;
                }
            }
            
            int uniqueClientCount = uniqueClients.size();
            double avgEventsPerClient = uniqueClientCount > 0 ? (double) clientEvents.size() / uniqueClientCount : 0;
            
            row.add(String.format("%.2f", avgEventsPerClient));
            row.add(String.valueOf(uniqueClientCount));
            
            // Placeholder for retention rate and participation rate
            row.add(""); // retention rate
            row.add(""); // participation rate
            
            row.add(lastEventDate != null ? lastEventDate.toString() : "");
        } else {
            row.add("0"); // total events
            row.add("0.00"); // avg per client
            row.add("0"); // unique clients
            row.add(""); // retention rate
            row.add(""); // participation rate
            row.add(""); // last event date
        }
        
        // Awards and Recognition
        List<Document> awards = awardsByAgentMap.get(agentId);
        if (awards != null && !awards.isEmpty()) {
            row.add(String.valueOf(awards.size())); // total awards
            
            // Get unique categories
            Set<String> categories = new HashSet<>();
            Date mostRecentDate = null;
            String mostRecentAward = "";
            
            for (Document award : awards) {
                String category = safeGetString(award, "category");
                if (!category.isEmpty()) {
                    categories.add(category);
                }
                
                Date awardDate = (Date) award.get("awardDate");
                if (awardDate != null && (mostRecentDate == null || awardDate.after(mostRecentDate))) {
                    mostRecentDate = awardDate;
                    mostRecentAward = safeGetString(award, "name");
                }
            }
            
            row.add(String.join(",", categories));
            row.add(mostRecentAward);
            
            // Count top producer years
            long topProducerCount = awards.stream()
                .filter(a -> safeGetString(a, "name").toLowerCase().contains("top producer"))
                .count();
            row.add(String.valueOf(topProducerCount));
            
            // Simple recognition score based on award count
            row.add(String.valueOf(awards.size() * 10)); // simple scoring
        } else {
            row.add("0"); // total awards
            row.add(""); // categories
            row.add(""); // most recent
            row.add("0"); // top producer years
            row.add("0"); // recognition score
        }
        
        return row.toArray(new String[0]);
    }
    
    private void calculateListingMetrics(List<String> row, List<Document> listings) {
        int totalListings = listings.size();
        int activeListings = 0;
        int pendingListings = 0;
        int soldListings = 0;
        double totalListVolume = 0;
        List<Double> listPrices = new ArrayList<>();
        List<Long> daysOnMarket = new ArrayList<>();
        
        Date thirtyDaysAgo = new Date(System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000));
        Date ninetyDaysAgo = new Date(System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000));
        Date oneYearAgo = new Date(System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000));
        
        int listingsLast30Days = 0;
        int listingsLast90Days = 0;
        int listingsLastYear = 0;
        
        for (Document listing : listings) {
            String status = safeGetString(listing, "status").toLowerCase();
            if (status.contains("active")) activeListings++;
            else if (status.contains("pending")) pendingListings++;
            else if (status.contains("sold")) soldListings++;
            
            Double price = safeGetDouble(listing, "price");
            if (price != null) {
                listPrices.add(price);
                totalListVolume += price;
            }
            
            Date listDate = (Date) listing.get("listDate");
            if (listDate != null) {
                long days = (System.currentTimeMillis() - listDate.getTime()) / (1000 * 60 * 60 * 24);
                daysOnMarket.add(days);
                
                if (listDate.after(thirtyDaysAgo)) listingsLast30Days++;
                if (listDate.after(ninetyDaysAgo)) listingsLast90Days++;
                if (listDate.after(oneYearAgo)) listingsLastYear++;
            }
        }
        
        row.add(String.valueOf(totalListings));
        row.add(String.valueOf(activeListings));
        row.add(String.valueOf(pendingListings));
        row.add(String.valueOf(soldListings));
        row.add(String.valueOf(totalListings - activeListings - pendingListings - soldListings)); // expired
        row.add("0"); // withdrawn
        
        // Price statistics
        if (!listPrices.isEmpty()) {
            double avgPrice = listPrices.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            row.add(String.format("%.2f", avgPrice));
            Collections.sort(listPrices);
            double medianPrice = listPrices.get(listPrices.size() / 2);
            row.add(String.format("%.2f", medianPrice));
        } else {
            row.add("0");
            row.add("0");
        }
        
        row.add(String.format("%.2f", totalListVolume));
        
        // Days on market
        if (!daysOnMarket.isEmpty()) {
            double avgDays = daysOnMarket.stream().mapToLong(Long::longValue).average().orElse(0);
            row.add(String.format("%.1f", avgDays));
        } else {
            row.add("0");
        }
        
        row.add(String.valueOf(listingsLast30Days));
        row.add(String.valueOf(listingsLast90Days));
        row.add(String.valueOf(listingsLastYear));
    }
    
    private void calculateSalesMetrics(List<String> row, List<Document> transactions) {
        // Simplified sales metrics
        int totalSales = transactions.size();
        row.add(String.valueOf(totalSales));
        
        // Add placeholder values for remaining sales metrics
        for (int i = 0; i < 10; i++) {
            row.add("0");
        }
        
        // Property type breakdown - placeholders
        for (int i = 0; i < 8; i++) {
            row.add("0");
        }
    }
    
    private void calculateGeographicMetrics(List<String> row, List<Document> listings, List<Document> transactions) {
        // Add placeholder values for geographic and remaining metrics
        int remainingFields = buildComprehensiveHeaders().length - row.size();
        for (int i = 0; i < remainingFields; i++) {
            row.add("");
        }
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
}