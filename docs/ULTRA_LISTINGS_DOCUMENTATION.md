# Ultra Listings Exporter Documentation

## Executive Summary

The Ultra Listings Exporter provides a comprehensive view of all property listings in the system with extensive enrichment from related collections. Each row represents a single listing with detailed information including property characteristics, agent details, market profiles, location data, and search metadata. This export is designed for in-depth market analysis, property valuation studies, and agent performance tracking.

**Current Version**: Optimized export with ~192 human-readable columns (removed meaningless fields that were always false/null)
**Performance**: ~3,500 listings/second
**Output Format**: CSV with human-readable column headers

## Key Improvements
- Human-readable column headers for end users
- Removed ~67 meaningless columns that only contained false/null values
- Fixed brokerage city extraction from offices array
- Resolved all foreign keys to human-readable values
- Maintained high performance with optimized memory management

## Column Groups and Descriptions

### 1. Core Listing Information (8 columns)
**Description**: Primary listing identification and status
- `Listing ID` - Unique identifier for the listing
- `MLS Number` - Multiple Listing Service identifier  
- `Status` - Current listing status (active, pending, sold, expired)
- `Status Category` - Broader status categorization
- `List Date` - Date property was listed
- `Days on Market` - Calculated days since listing
- `Expiration Date` - Listing expiration date
- `Last Update` - Date of last modification

### 2. Pricing Information (10 columns)
**Description**: Comprehensive pricing and fee details
- `List Price` - Current asking price
- `Price per Sq Ft` - Price per square foot
- `Price History Count` - Number of price changes
- `Original List Price` - Initial listing price
- `Current Price Index` - Relative price indicator
- `HOA Fee` - Homeowners association fee
- `Listing Type` - Type of listing (exclusive, open, etc.)
- `Listing Agreement` - Agreement type
- `Commission Percent` - Commission percentage
- `Showing Requirements` - Requirements for property showings

### 3. Property Details (15 columns)
**Description**: Core property characteristics
- `Property ID` - Property identifier
- `Property Type` - Type of property (single family, condo, etc.)
- `Property Sub-Type` - More specific property classification
- `Year Built` - Year of construction
- `Square Feet` - Total living area
- `Lot Size` - Lot size in square feet
- `Bedrooms` - Number of bedrooms
- `Full Bathrooms` - Number of full bathrooms
- `Half Bathrooms` - Number of half bathrooms
- `Total Bathrooms` - Combined bathroom count
- `Rooms` - Total room count
- `Stories` - Number of stories
- `Garage Spaces` - Garage parking spaces
- `Carport Spaces` - Carport spaces
- `Parking Spaces` - Total parking spaces

### 4. Location Information (12 columns)
**Description**: Complete address and geographic details
- `Street Address` - Full street address
- `Unit Number` - Apartment or unit number
- `City` - City name
- `State` - State abbreviation
- `ZIP Code` - ZIP code
- `County` - County name
- `Subdivision` - Subdivision name
- `Latitude` - GPS latitude coordinate
- `Longitude` - GPS longitude coordinate
- `Geo Precision` - Accuracy of coordinates
- `Neighborhood` - Neighborhood name
- `Directions` - Driving directions

### 5. School Information (12 columns)
**Description**: School district and individual school details
- `Elementary School` - Assigned elementary school
- `Elementary Rating` - School rating/score
- `Elementary District` - Elementary school district
- `Elementary Distance (mi)` - Distance to school
- `Middle School` - Assigned middle school
- `Middle Rating` - School rating/score
- `Middle District` - Middle school district
- `Middle Distance (mi)` - Distance to school
- `High School` - Assigned high school
- `High Rating` - School rating/score
- `High District` - High school district
- `High Distance (mi)` - Distance to school

### 6. Property Tags (25 columns)
**Description**: Boolean indicators for special property features
- `Tag: Featured` - Featured listing
- `Tag: Coming soon` - Coming soon to market
- `Tag: Price reduced` - Recently reduced price
- `Tag: Open house` - Open house scheduled
- `Tag: Virtual tour` - Virtual tour available
- `Tag: New construction` - Newly built property
- `Tag: Foreclosure` - Foreclosure property
- `Tag: Short sale` - Short sale listing
- `Tag: Auction` - Auction property
- `Tag: Bank owned` - REO/bank owned
- `Tag: Reo` - Real estate owned
- `Tag: Distressed` - Distressed sale
- `Tag: Fixer upper` - Needs renovation
- `Tag: As is` - Sold as-is condition
- `Tag: Estate sale` - Estate sale property
- `Tag: Probate` - Probate sale
- `Tag: Relocation` - Relocation sale
- `Tag: Corporate owned` - Corporate-owned property
- `Tag: Government owned` - Government property
- `Tag: Tax lien` - Tax lien property
- `Tag: Pre foreclosure` - Pre-foreclosure status
- `Tag: Hud home` - HUD home
- `Tag: Fannie mae` - Fannie Mae property
- `Tag: Freddie mac` - Freddie Mac property
- `Tag: Va owned` - VA-owned property

### 7. Current Agent Information (18 columns)
**Description**: Listing agent details and credentials
- `Agent ID` - Agent identifier
- `Agent Name` - Full agent name
- `Agent Email` - Agent email address
- `Agent Phone` - Agent phone number
- `Agent License #` - License number
- `Agent Website` - Agent website URL
- `Agent Bio Length` - Length of agent biography
- `Agent Photo URL` - Agent photo link
- `Agent Years Experience` - Years in real estate
- `Agent Designations` - Professional designations
- `Agent Specialties` - Areas of expertise
- `Agent Languages` - Languages spoken
- `Agent Education` - Educational background
- `Agent Social Media` - Social media links
- `Agent Rating (Avg)` - Average client rating
- `Agent Rating Count` - Number of ratings
- `Agent Sales Last Year` - Sales in past year
- `Agent Listings Last Year` - Listings in past year

### 8. Agent Personal Information (7 columns)
**Description**: Additional agent contact details
- `Agent Person ID` - Person record identifier
- `Agent Address` - Agent office/home address
- `Agent City` - Agent city
- `Agent State` - Agent state
- `Agent ZIP Code` - Agent ZIP code
- `Agent Person Languages` - Languages from person record
- `Agent Person Specialties` - Specialties from person record

### 9. Brokerage Information (11 columns)
**Description**: Real estate brokerage details
- `Brokerage ID` - Brokerage identifier
- `Brokerage Name` - Brokerage company name
- `Brokerage Phone` - Main phone number
- `Brokerage Email` - Contact email
- `Brokerage Website` - Company website
- `Brokerage Address` - Office address
- `Brokerage City` - Office city
- `Brokerage State` - Office state
- `Brokerage ZIP Code` - Office ZIP code
- `Brokerage Type` - Type of brokerage
- `Brokerage Year Established` - Year founded

### 10. Marketing and Media (9 columns)
**Description**: Marketing materials and property media
- `Picture Count` - Number of photos
- `Virtual Tour URL` - Virtual tour link
- `Video URL` - Property video link
- `Walkthrough Video URL` - Walkthrough video
- `Drone Video URL` - Aerial video link
- `Matterport URL` - 3D tour link
- `Marketing Remarks` - Public marketing description
- `Private Remarks` - Agent-only remarks
- `Property Description` - Detailed description

### 11. Showing Information (6 columns)
**Description**: Property showing details and access
- `Showing Instructions` - Instructions for showings
- `Showing Contact` - Contact for showings
- `Lockbox Type` - Type of lockbox
- `Lockbox Code` - Lockbox access code
- `Gate Code` - Gate/entry code
- `Alarm Info` - Alarm system information

### 12. Transaction History (13 columns)
**Description**: Sale information if property sold
- `Has Transaction` - Whether property has sold
- `Sale Date` - Date of sale
- `Sale Price` - Final sale price
- `Sale to List Ratio (%)` - Sale price vs list price percentage
- `Days to Sell` - Days from listing to sale
- `Buyer Agent ID` - Buyer's agent identifier
- `Buyer Agent Name` - Buyer's agent name
- `Buyer Brokerage` - Buyer's brokerage
- `Buyers Count` - Number of buyers
- `Buyer Names` - Names of buyers
- `Sellers Count` - Number of sellers
- `Seller Names` - Names of sellers
- `Financing Type` - Type of financing used

### 13. Market Profile Data (11 columns)
**Description**: Area market statistics and trends
- `Market Area` - Market area name
- `Market Median List Price` - Area median list price
- `Market Median Sale Price` - Area median sale price
- `Market Avg Days on Market` - Area average DOM
- `Market Inventory Count` - Active listings in area
- `Market New Listings (30d)` - New listings last 30 days
- `Market Sold Listings (30d)` - Sales last 30 days
- `Market Price Trend (3m)` - 3-month price trend
- `Market Price Trend (12m)` - 12-month price trend
- `Market Absorption Rate` - Rate of sales
- `Market Months of Inventory` - Supply/demand ratio

### 14. Demographics (9 columns)
**Description**: Area demographic information
- `Market Population` - Area population
- `Market Households` - Number of households
- `Market Median Income` - Median household income
- `Market Age Distribution` - Age demographics
- `Market Income Distribution` - Income brackets
- `Market Marital Distribution` - Marital status
- `Market Education Level` - Education levels
- `Market Employment Rate` - Employment percentage
- `Market Owner-Occupied Rate` - Owner vs renter ratio

### 15. City Data (9 columns)
**Description**: City-level statistics
- `City Population` - City population
- `City Median Income` - City median income
- `City Median Home Value` - Median home value
- `City Unemployment Rate` - Unemployment rate
- `City Cost of Living Index` - Cost of living index
- `City Crime Rate` - Crime statistics
- `City School Rating` - Overall school rating
- `City Walk Score` - Walkability score
- `City Transit Score` - Public transit score

### 16. Enhanced Tags and Search (5 columns)
**Description**: Additional categorization and search metadata
- `Listing Lifestyles` - Lifestyle tags
- `Listing Tags` - Additional search tags
- `Co-Ownership Type` - Ownership structure
- `Tag Categories` - Tag categorizations
- `Tag Weights Sum` - Combined tag weights

### 17. Feeder Markets (4 columns)
**Description**: Related market areas
- `Feeder Market Count` - Number of feeder markets
- `Primary Feeder Market` - Main feeder market
- `Secondary Feeder Markets` - Additional markets
- `Feeder Market Distances` - Distances to markets

## Usage Notes

1. **File Size**: Approximately 92MB for full export of 64,363 listings
2. **Performance**: Exports complete in ~20 seconds
3. **Memory Requirements**: Requires 20GB heap allocation for optimal performance
4. **Update Frequency**: Run daily or as needed for current data

## Running the Export

```bash
./gradlew runUltraListings
```

Output file: `output/all_listings_ultra_comprehensive_YYYYMMDD_HHMMSS.csv`