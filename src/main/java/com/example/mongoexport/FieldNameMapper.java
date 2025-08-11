package com.example.mongoexport;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps MongoDB field paths to business-readable column names.
 * This centralizes all field naming logic for consistent, user-friendly exports.
 */
public class FieldNameMapper
{
    private static final Map<String, String> FIELD_MAPPINGS = new HashMap<>();

    static
    {
        // Core document fields
        FIELD_MAPPINGS.put("_id", "Record ID");
        FIELD_MAPPINGS.put("__v", "Version");

        // Listing core fields
        FIELD_MAPPINGS.put("mlsNumber", "MLS Number");
        FIELD_MAPPINGS.put("listingId", "Listing ID");
        FIELD_MAPPINGS.put("status", "Status");
        FIELD_MAPPINGS.put("listPrice", "List Price");
        FIELD_MAPPINGS.put("originalListPrice", "Original List Price");
        FIELD_MAPPINGS.put("soldPrice", "Sold Price");
        FIELD_MAPPINGS.put("listDate", "List Date");
        FIELD_MAPPINGS.put("dateListed", "Date Listed");
        FIELD_MAPPINGS.put("soldDate", "Sold Date");
        FIELD_MAPPINGS.put("daysOnMarket", "Days on Market");
        FIELD_MAPPINGS.put("cumulativeDaysOnMarket", "Cumulative Days on Market");
        FIELD_MAPPINGS.put("archived", "Archive Date");
        FIELD_MAPPINGS.put("deleted", "Is Deleted");
        FIELD_MAPPINGS.put("importDate", "Import Date");

        // Property details
        FIELD_MAPPINGS.put("property", "Property ID");
        FIELD_MAPPINGS.put("property._id", "Property Record ID");
        FIELD_MAPPINGS.put("propertyType", "Property Type");
        FIELD_MAPPINGS.put("propertySubtype", "Property Subtype");
        FIELD_MAPPINGS.put("bedrooms", "Bedrooms");
        FIELD_MAPPINGS.put("bathrooms", "Bathrooms");
        FIELD_MAPPINGS.put("fullBathrooms", "Full Bathrooms");
        FIELD_MAPPINGS.put("halfBathrooms", "Half Bathrooms");
        FIELD_MAPPINGS.put("squareFeet", "Square Feet");
        FIELD_MAPPINGS.put("lotSize", "Lot Size");
        FIELD_MAPPINGS.put("lotSizeAcres", "Lot Size (Acres)");
        FIELD_MAPPINGS.put("yearBuilt", "Year Built");
        FIELD_MAPPINGS.put("stories", "Stories");
        FIELD_MAPPINGS.put("rooms", "Total Rooms");

        // Address fields
        FIELD_MAPPINGS.put("fullAddress", "Full Address");
        FIELD_MAPPINGS.put("streetAddress", "Street Address");
        FIELD_MAPPINGS.put("streetNumber", "Street Number");
        FIELD_MAPPINGS.put("streetName", "Street Name");
        FIELD_MAPPINGS.put("streetSuffix", "Street Suffix");
        FIELD_MAPPINGS.put("unitNumber", "Unit Number");
        FIELD_MAPPINGS.put("city", "City");
        FIELD_MAPPINGS.put("state", "State");
        FIELD_MAPPINGS.put("stateCode", "State Code");
        FIELD_MAPPINGS.put("zipCode", "ZIP Code");
        FIELD_MAPPINGS.put("postalCode", "Postal Code");
        FIELD_MAPPINGS.put("county", "County");
        FIELD_MAPPINGS.put("latitude", "Latitude");
        FIELD_MAPPINGS.put("longitude", "Longitude");
        FIELD_MAPPINGS.put("location", "Location");
        FIELD_MAPPINGS.put("location.latitude", "Location Latitude");
        FIELD_MAPPINGS.put("location.longitude", "Location Longitude");
        FIELD_MAPPINGS.put("displayAddress", "Display Address");

        // Features and amenities
        FIELD_MAPPINGS.put("hasBasement", "Has Basement");
        FIELD_MAPPINGS.put("hasFireplace", "Has Fireplace");
        FIELD_MAPPINGS.put("hasGarage", "Has Garage");
        FIELD_MAPPINGS.put("hasPool", "Has Pool");
        FIELD_MAPPINGS.put("hasDeck", "Has Deck");
        FIELD_MAPPINGS.put("hasFencedYard", "Has Fenced Yard");
        FIELD_MAPPINGS.put("hasGatedEntry", "Has Gated Entry");
        FIELD_MAPPINGS.put("hasOnsiteLaundry", "Has Onsite Laundry");
        FIELD_MAPPINGS.put("exteriorType", "Exterior Type");
        FIELD_MAPPINGS.put("roofType", "Roof Type");
        FIELD_MAPPINGS.put("appliances", "Appliances");
        FIELD_MAPPINGS.put("coolingSystems", "Cooling Systems");
        FIELD_MAPPINGS.put("heatingSystems", "Heating Systems");
        FIELD_MAPPINGS.put("flooring", "Flooring");
        FIELD_MAPPINGS.put("parking", "Parking");
        FIELD_MAPPINGS.put("parkingSpaces", "Parking Spaces");

        // Marketing fields
        FIELD_MAPPINGS.put("description", "Description");
        FIELD_MAPPINGS.put("description.en", "Description (English)");
        FIELD_MAPPINGS.put("publicRemarks", "Public Remarks");
        FIELD_MAPPINGS.put("privateRemarks", "Private Remarks");
        FIELD_MAPPINGS.put("marketingRemarks", "Marketing Remarks");
        FIELD_MAPPINGS.put("virtualTourUrl", "Virtual Tour URL");
        FIELD_MAPPINGS.put("photos", "Photos");
        FIELD_MAPPINGS.put("photosCount", "Photos Count");

        // Agent fields
        FIELD_MAPPINGS.put("listingAgentId", "Listing Agent ID");
        FIELD_MAPPINGS.put("listingAgent", "Listing Agent ID");
        FIELD_MAPPINGS.put("listingAgent.name", "Listing Agent Name");
        FIELD_MAPPINGS.put("listingAgent.email", "Listing Agent Email");
        FIELD_MAPPINGS.put("listingAgent.phone", "Listing Agent Phone");
        FIELD_MAPPINGS.put("listingAgent.licenseNumber", "Listing Agent License");
        FIELD_MAPPINGS.put("listingAgents", "Listing Agents");
        FIELD_MAPPINGS.put("listingAgents[]", "Listing Agents Array");
        FIELD_MAPPINGS.put("listingAgents[].name", "Listing Agent Name");
        FIELD_MAPPINGS.put("listingAgents[].email", "Listing Agent Email");
        FIELD_MAPPINGS.put("listingAgents[].phone", "Listing Agent Phone");
        FIELD_MAPPINGS.put("listingAgents[].isPrimary", "Is Primary Agent");

        // Brokerage fields
        FIELD_MAPPINGS.put("listingBrokerageId", "Listing Brokerage ID");
        FIELD_MAPPINGS.put("listingBrokerage", "Listing Brokerage ID");
        FIELD_MAPPINGS.put("listingBrokerage.name", "Listing Brokerage Name");
        FIELD_MAPPINGS.put("listingBrokerage.phone", "Listing Brokerage Phone");
        FIELD_MAPPINGS.put("listingBrokerage.address", "Listing Brokerage Address");
        FIELD_MAPPINGS.put("listingBrokerage.email", "Listing Brokerage Email");

        // Financial fields
        FIELD_MAPPINGS.put("taxAmount", "Tax Amount");
        FIELD_MAPPINGS.put("taxYear", "Tax Year");
        FIELD_MAPPINGS.put("assessedValue", "Assessed Value");
        FIELD_MAPPINGS.put("hoaFees", "HOA Fees");
        FIELD_MAPPINGS.put("hoaFrequency", "HOA Fee Frequency");
        FIELD_MAPPINGS.put("fees", "Fees");
        FIELD_MAPPINGS.put("fees[]", "Fees Array");
        FIELD_MAPPINGS.put("fees[].feeAmount", "Fee Amount");
        FIELD_MAPPINGS.put("fees[].feeType", "Fee Type");
        FIELD_MAPPINGS.put("fees[].feeFrequency", "Fee Frequency");

        // School fields
        FIELD_MAPPINGS.put("elementarySchool", "Elementary School");
        FIELD_MAPPINGS.put("middleSchool", "Middle School");
        FIELD_MAPPINGS.put("highSchool", "High School");
        FIELD_MAPPINGS.put("schoolDistrict", "School District");

        // HOA and community
        FIELD_MAPPINGS.put("hoaName", "HOA Name");
        FIELD_MAPPINGS.put("subdivision", "Subdivision");
        FIELD_MAPPINGS.put("community", "Community");
        FIELD_MAPPINGS.put("communityFeatures", "Community Features");

        // Transaction fields
        FIELD_MAPPINGS.put("buyerAgentId", "Buyer Agent ID");
        FIELD_MAPPINGS.put("buyerAgent", "Buyer Agent ID");
        FIELD_MAPPINGS.put("buyerAgent.name", "Buyer Agent Name");
        FIELD_MAPPINGS.put("buyerBrokerageId", "Buyer Brokerage ID");
        FIELD_MAPPINGS.put("buyerBrokerage", "Buyer Brokerage ID");
        FIELD_MAPPINGS.put("buyerBrokerage.name", "Buyer Brokerage Name");

        // Showing and access
        FIELD_MAPPINGS.put("showingInstructions", "Showing Instructions");
        FIELD_MAPPINGS.put("showingContactName", "Showing Contact Name");
        FIELD_MAPPINGS.put("showingContactPhone", "Showing Contact Phone");
        FIELD_MAPPINGS.put("accessCode", "Access Code");
        FIELD_MAPPINGS.put("lockboxType", "Lockbox Type");
        FIELD_MAPPINGS.put("lockboxCode", "Lockbox Code");
        FIELD_MAPPINGS.put("alwaysEmailAgent", "Always Email Agent");

        // Status and timestamps
        FIELD_MAPPINGS.put("listingStatus", "Listing Status");
        FIELD_MAPPINGS.put("standardStatus", "Standard Status");
        FIELD_MAPPINGS.put("statusChangeDate", "Status Change Date");
        FIELD_MAPPINGS.put("pendingDate", "Pending Date");
        FIELD_MAPPINGS.put("withdrawnDate", "Withdrawn Date");
        FIELD_MAPPINGS.put("canceledDate", "Canceled Date");
        FIELD_MAPPINGS.put("expiredDate", "Expired Date");
        FIELD_MAPPINGS.put("onMarketDate", "On Market Date");
        FIELD_MAPPINGS.put("offMarketDate", "Off Market Date");
        FIELD_MAPPINGS.put("createdAt", "Created At");
        FIELD_MAPPINGS.put("updatedAt", "Updated At");
        FIELD_MAPPINGS.put("lastModified", "Last Modified");

        // Source and MLS fields
        FIELD_MAPPINGS.put("sourceSystemId", "Source System ID");
        FIELD_MAPPINGS.put("sourceSystemName", "Source System Name");
        FIELD_MAPPINGS.put("mlsId", "MLS ID");
        FIELD_MAPPINGS.put("mlsName", "MLS Name");
        FIELD_MAPPINGS.put("mlsAreaMajor", "MLS Area Major");
        FIELD_MAPPINGS.put("mlsAreaMinor", "MLS Area Minor");

        // Additional property features
        FIELD_MAPPINGS.put("waterfront", "Waterfront");
        FIELD_MAPPINGS.put("waterfrontFeatures", "Waterfront Features");
        FIELD_MAPPINGS.put("view", "View");
        FIELD_MAPPINGS.put("viewDescription", "View Description");
        FIELD_MAPPINGS.put("lotFeatures", "Lot Features");
        FIELD_MAPPINGS.put("constructionMaterials", "Construction Materials");
        FIELD_MAPPINGS.put("foundationDetails", "Foundation Details");
        FIELD_MAPPINGS.put("basementType", "Basement Type");
        FIELD_MAPPINGS.put("basementFinished", "Basement Finished");

        // Utility fields
        FIELD_MAPPINGS.put("utilities", "Utilities");
        FIELD_MAPPINGS.put("waterSource", "Water Source");
        FIELD_MAPPINGS.put("sewerType", "Sewer Type");
        FIELD_MAPPINGS.put("electricService", "Electric Service");
        FIELD_MAPPINGS.put("gasType", "Gas Type");

        // Occupancy and possession
        FIELD_MAPPINGS.put("occupancy", "Occupancy");
        FIELD_MAPPINGS.put("possession", "Possession");
        FIELD_MAPPINGS.put("possessionDate", "Possession Date");
        FIELD_MAPPINGS.put("rentAmount", "Rent Amount");
        FIELD_MAPPINGS.put("leaseExpiration", "Lease Expiration");
        FIELD_MAPPINGS.put("tenantPays", "Tenant Pays");

        // Reference fields (for relationships)
        FIELD_MAPPINGS.put("@reference", "Reference");
        FIELD_MAPPINGS.put("_id.@reference", "ID Reference");
        FIELD_MAPPINGS.put("property.@reference", "Property Reference");
        FIELD_MAPPINGS.put("listingAgentId.@reference", "Listing Agent Reference");

        // Custom and other fields
        FIELD_MAPPINGS.put("customFields", "Custom Fields");
        FIELD_MAPPINGS.put("additionalInfo", "Additional Info");
        FIELD_MAPPINGS.put("notes", "Notes");
        FIELD_MAPPINGS.put("internalNotes", "Internal Notes");
    }

    /**
     * Get business-readable name for a MongoDB field path
     * FIXED: Clean technical markers before mapping
     *
     * @param fieldPath MongoDB dot-notation field path
     * @return Business-readable column name
     */
    public static String getBusinessName(String fieldPath)
    {
        // CRITICAL FIX: Clean up technical markers FIRST
        String cleanPath = fieldPath.replace(".@expanded", "").replace("_expanded", "").replace(".@reference", "").replaceAll("\\[\\d+\\]", "[]"); // Also handle array indices

        // Check for direct mapping with clean path
        if (FIELD_MAPPINGS.containsKey(cleanPath))
        {
            return FIELD_MAPPINGS.get(cleanPath);
        }

        // Handle array notation for the cleaned path
        String normalizedPath = cleanPath.replaceAll("\\[\\]", "");
        if (FIELD_MAPPINGS.containsKey(normalizedPath))
        {
            return FIELD_MAPPINGS.get(normalizedPath) + " (List)";
        }

        // Handle nested fields by checking parent paths
        if (fieldPath.contains("."))
        {
            String[] parts = fieldPath.split("\\.");

            // Try to build a readable name from parts
            StringBuilder readableName = new StringBuilder();
            for (String part : parts)
            {
                // Skip array indices
                if (part.matches("\\d+")) continue;

                // Clean up array notation
                part = part.replaceAll("\\[.*?\\]", "");

                // Skip certain technical fields
                if (part.equals("@reference") || part.equals("_id"))
                {
                    continue;
                }

                // Capitalize first letter and add to name
                if (!readableName.isEmpty())
                {
                    readableName.append(" ");
                }
                readableName.append(capitalizeFirst(camelToWords(part)));
            }

            if (!readableName.isEmpty())
            {
                return readableName.toString();
            }
        }

        // Default: convert camelCase to readable format
        return capitalizeFirst(camelToWords(fieldPath));
    }

    /**
     * Convert camelCase to space-separated words
     */
    private static String camelToWords(String camelCase)
    {
        if (camelCase == null || camelCase.isEmpty())
        {
            return camelCase;
        }

        // Handle special cases
        camelCase = camelCase.replace("_id", "ID");
        camelCase = camelCase.replace("__v", "Version");

        // Insert spaces before capitals
        String result = camelCase.replaceAll("([a-z])([A-Z])", "$1 $2");

        // Handle acronyms
        result = result.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");

        return result;
    }

    /**
     * Capitalize first letter of a string
     */
    private static String capitalizeFirst(String str)
    {
        if (str == null || str.isEmpty())
        {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Add a custom field mapping
     */
    public static void addMapping(String fieldPath, String businessName)
    {
        FIELD_MAPPINGS.put(fieldPath, businessName);
    }

    /**
     * Check if a field path has a mapping
     */
    public static boolean hasMapping(String fieldPath)
    {
        return FIELD_MAPPINGS.containsKey(fieldPath) || FIELD_MAPPINGS.containsKey(fieldPath.replaceAll("\\[\\d+\\]", "[]"));
    }
}