# Ultra Listings Exporter Documentation

## Executive Summary

The Ultra Listings Exporter provides a comprehensive view of all property listings in the system with extensive enrichment from related collections. Each row represents a single listing with detailed information including property characteristics, agent details, market profiles, location data, and search metadata. This export is designed for in-depth market analysis, property valuation studies, and agent performance tracking.

**Note**: A cleaned version (`UltraListingsExporterCleaned`) is available that removes ~67 meaningless columns (always false/null fields), reducing the export from 259 to ~192 columns while maintaining all meaningful data.

## Column Groups and Descriptions

### 1. Core Listing Identifiers and Basic Information (8 columns)
**Description**: Primary listing identification and fundamental property details
- `listing_id` - MongoDB ObjectId of the listing
- `mls_number` - Multiple Listing Service identifier
- `property_type` - Type of property (single family, condo, townhome, etc.)
- `status` - Current listing status (active, pending, sold, expired)
- `list_date` - Date property was listed
- `expiration_date` - Listing expiration date
- `days_on_market` - Calculated days since listing
- `created_date` - Date record was created in system

### 2. Address and Location Details (20 columns)
**Description**: Complete property location information with geocoding
- `street_address` - Full street address
- `unit_number` - Apartment or unit number
- `city` - City name
- `state` - State abbreviation
- `zipcode` - ZIP code
- `county` - County name
- `subdivision` - Subdivision or neighborhood name
- `school_district` - School district
- `elementary_school` - Elementary school zone
- `middle_school` - Middle school zone
- `high_school` - High school zone
- `latitude` - GPS latitude coordinate
- `longitude` - GPS longitude coordinate
- `parcel_number` - Tax parcel identifier
- `legal_description` - Legal property description
- `zoning` - Zoning classification
- `flood_zone` - Flood zone designation
- `census_tract` - Census tract number
- `map_coordinates` - Additional mapping coordinates
- `directions` - Driving directions to property

### 3. Price and Financial Information (15 columns)
**Description**: Comprehensive pricing, tax, and financial details
- `list_price` - Current asking price
- `original_list_price` - Initial listing price
- `price_per_sqft` - Price per square foot
- `previous_list_price` - Prior listing price if changed
- `price_change_date` - Date of last price change
- `price_change_amount` - Amount of price change
- `tax_assessed_value` - Property tax assessment
- `annual_property_tax` - Annual tax amount
- `tax_year` - Tax assessment year
- `hoa_fee` - Homeowners association fee
- `hoa_frequency` - HOA fee payment frequency
- `special_assessments` - Special assessment amounts
- `estimated_mortgage` - Estimated monthly mortgage
- `estimated_insurance` - Estimated insurance cost
- `total_monthly_cost` - Estimated total monthly cost

### 4. Property Characteristics (35 columns)
**Description**: Detailed physical property attributes and features
- `property_style` - Architectural style
- `year_built` - Year of construction
- `total_sqft` - Total square footage
- `living_sqft` - Living area square footage
- `lot_size_sqft` - Lot size in square feet
- `lot_size_acres` - Lot size in acres
- `bedrooms` - Number of bedrooms
- `full_bathrooms` - Number of full bathrooms
- `half_bathrooms` - Number of half bathrooms
- `total_rooms` - Total room count
- `stories` - Number of stories
- `garage_spaces` - Garage parking spaces
- `carport_spaces` - Carport spaces
- `parking_description` - Additional parking details
- `basement_type` - Basement type (finished, unfinished, none)
- `basement_sqft` - Basement square footage
- `foundation_type` - Foundation type
- `roof_type` - Roofing material
- `exterior_material` - Exterior construction material
- `heating_type` - Heating system type
- `cooling_type` - Air conditioning type
- `water_source` - Water supply source
- `sewer_type` - Sewer system type
- `utilities` - Available utilities
- `pool` - Pool (yes/no)
- `pool_type` - Type of pool if present
- `spa` - Spa/hot tub (yes/no)
- `fireplace_count` - Number of fireplaces
- `view_description` - View description
- `waterfront` - Waterfront property (yes/no)
- `waterfront_type` - Type of waterfront
- `energy_features` - Energy efficiency features
- `accessibility_features` - ADA/accessibility features
- `security_features` - Security system features
- `smart_home_features` - Smart home technology

### 5. Interior Features and Amenities (25 columns)
**Description**: Interior room details and amenity information
- `kitchen_features` - Kitchen amenities and appliances
- `master_bedroom_features` - Master bedroom details
- `bathroom_features` - Bathroom amenities
- `flooring_types` - Types of flooring
- `window_features` - Window types and features
- `laundry_features` - Laundry location and features
- `storage_features` - Storage and closet details
- `ceiling_features` - Ceiling types and heights
- `living_room_features` - Living room amenities
- `dining_room_features` - Dining room details
- `family_room_features` - Family room amenities
- `office_features` - Home office details
- `bonus_room_features` - Bonus/flex room details
- `appliances_included` - Included appliances list
- `appliance_descriptions` - Detailed appliance info
- `interior_paint_condition` - Interior paint condition
- `carpet_condition` - Carpet condition
- `hardwood_condition` - Hardwood floor condition
- `plumbing_updates` - Recent plumbing updates
- `electrical_updates` - Recent electrical updates
- `hvac_age` - HVAC system age
- `roof_age` - Roof age
- `window_age` - Window age
- `recent_renovations` - Recent renovation details
- `renovation_year` - Year of major renovations

### 6. Listing Agent Information (28 columns)
**Description**: Complete details about the listing agent and their performance
- `listing_agent_id` - Agent's unique identifier
- `listing_agent_name` - Agent's full name
- `listing_agent_email` - Agent email
- `listing_agent_phone` - Agent phone number
- `listing_agent_mobile` - Agent mobile number
- `listing_agent_license` - Agent license number
- `listing_agent_years_exp` - Years of experience
- `listing_agent_designations` - Professional designations
- `listing_agent_specialties` - Agent specialties
- `listing_agent_bio` - Agent biography excerpt
- `listing_agent_website` - Agent website
- `listing_agent_photo_url` - Agent photo URL
- `listing_agent_rating` - Agent rating/reviews
- `listing_agent_total_sales` - Total career sales
- `listing_agent_annual_sales` - Annual sales volume
- `listing_agent_active_listings` - Current active listings
- `listing_agent_sold_listings` - Total sold listings
- `listing_agent_avg_dom` - Average days on market
- `listing_agent_list_to_sold_ratio` - List to sold price ratio
- `listing_office_id` - Listing office ID
- `listing_office_name` - Listing office name
- `listing_office_phone` - Office phone
- `listing_office_email` - Office email
- `listing_office_address` - Office address
- `listing_office_website` - Office website
- `cobroke_agent_id` - Co-listing agent ID if applicable
- `cobroke_agent_name` - Co-listing agent name
- `commission_offered` - Commission offered to buyer's agent

### 7. Brokerage Information (15 columns)
**Description**: Details about the listing brokerage/real estate company
- `brokerage_id` - Brokerage unique identifier
- `brokerage_name` - Brokerage company name
- `brokerage_phone` - Main office phone
- `brokerage_email` - Main office email
- `brokerage_website` - Company website
- `brokerage_address` - Headquarters address
- `brokerage_city` - Brokerage city
- `brokerage_state` - Brokerage state
- `brokerage_zipcode` - Brokerage ZIP
- `brokerage_type` - Type of brokerage (franchise, independent, etc.)
- `brokerage_size` - Size classification
- `brokerage_year_established` - Year founded
- `brokerage_total_agents` - Total agent count
- `brokerage_market_share` - Local market share percentage
- `brokerage_specialties` - Company specialties

### 8. Market Profile Information (20 columns)
**Description**: Market area statistics and demographic information
- `market_area_name` - Market area designation
- `market_median_price` - Area median sale price
- `market_avg_price` - Area average sale price
- `market_price_trend` - Price trend (up/down/stable)
- `market_inventory_level` - Current inventory months
- `market_days_on_market` - Area average DOM
- `market_absorption_rate` - Market absorption rate
- `market_new_listings_count` - New listings this month
- `market_sold_count` - Properties sold this month
- `market_pending_count` - Pending sales count
- `market_price_per_sqft` - Area price per square foot
- `area_population` - Area population
- `area_median_income` - Median household income
- `area_employment_rate` - Employment rate
- `area_school_rating` - Average school rating
- `area_crime_index` - Crime index rating
- `area_walkability_score` - Walkability score
- `area_transit_score` - Public transit score
- `area_growth_rate` - Population growth rate
- `area_new_construction` - New construction percentage

### 9. City and Location Enrichment (15 columns)
**Description**: Enhanced city-level data from US cities database
- `city_population` - City population
- `city_median_income` - City median income
- `city_median_home_value` - City median home value
- `city_cost_of_living_index` - Cost of living index
- `city_unemployment_rate` - Unemployment rate
- `city_tax_rate` - Local tax rate
- `city_school_spending` - Per-pupil spending
- `city_crime_rate` - Crime rate per 1000
- `city_commute_time` - Average commute time
- `city_air_quality_index` - Air quality index
- `city_park_acres_per_1000` - Park space per capita
- `metro_area` - Metropolitan area name
- `metro_population` - Metro area population
- `distance_to_downtown` - Miles to city center
- `distance_to_airport` - Miles to nearest airport

### 10. Search and Visibility Metrics (12 columns)
**Description**: Property search visibility and online engagement metrics
- `search_views_count` - Total search views
- `search_saves_count` - Times saved by users
- `search_inquiries_count` - Number of inquiries
- `search_showing_requests` - Showing requests
- `virtual_tour_views` - Virtual tour view count
- `photo_views_count` - Photo gallery views
- `search_ranking_score` - Search result ranking
- `featured_listing` - Featured listing flag
- `premium_placement` - Premium placement flag
- `listing_quality_score` - Listing completeness score
- `days_since_last_update` - Days since listing updated
- `update_frequency` - How often listing updates

### 11. Marketing and Showing Information (15 columns)
**Description**: Marketing materials and showing logistics
- `marketing_remarks` - Public marketing description
- `private_remarks` - Agent-only remarks
- `showing_instructions` - Showing instructions
- `showing_contact` - Showing contact info
- `lockbox_type` - Type of lockbox
- `lockbox_code` - Lockbox access code
- `vacant_property` - Vacant property flag
- `tenant_occupied` - Tenant occupied flag
- `lease_expiration` - Lease expiration if applicable
- `possession_date` - Available possession date
- `exclusions` - Items excluded from sale
- `inclusions` - Items included in sale
- `virtual_tour_url` - Virtual tour link
- `video_url` - Property video link
- `brochure_url` - Marketing brochure link

### 12. Offer and Contract Details (10 columns)
**Description**: Offer management and contract status
- `offers_received` - Number of offers received
- `highest_offer` - Highest offer amount
- `offer_deadline` - Offer deadline date
- `under_contract` - Under contract flag
- `contract_date` - Contract acceptance date
- `contingencies` - Active contingencies
- `closing_date` - Scheduled closing date
- `escrow_company` - Escrow company name
- `title_company` - Title company name
- `home_warranty` - Home warranty included

### 13. Historical and Comparative Data (12 columns)
**Description**: Property history and market comparisons
- `previous_sale_date` - Last sale date
- `previous_sale_price` - Last sale price
- `price_appreciation` - Appreciation since last sale
- `ownership_length` - Current owner tenure
- `listing_history_count` - Times listed previously
- `average_neighborhood_price` - Neighborhood average
- `price_vs_neighborhood` - Price comparison percentage
- `similar_properties_for_sale` - Competing listings count
- `recent_neighborhood_sales` - Recent sales count
- `market_competition_index` - Competition level score
- `price_confidence_score` - Pricing accuracy score
- `likely_sale_timeframe` - Predicted sale timeframe

### 14. Tags and Categories (10 columns)
**Description**: Property tags and categorical classifications
- `primary_tag` - Main property tag
- `secondary_tags` - Additional tags
- `lifestyle_tags` - Lifestyle feature tags
- `investment_potential` - Investment rating
- `first_time_buyer_friendly` - First-time buyer suitable
- `luxury_property` - Luxury designation
- `green_certified` - Green certification
- `historic_property` - Historic designation
- `foreclosure_status` - Foreclosure/REO status
- `short_sale` - Short sale flag

### 15. Feeder Market Information (8 columns)
**Description**: Related market areas and migration patterns
- `primary_feeder_market` - Main feeder market
- `secondary_feeder_markets` - Other feeder markets
- `buyer_origin_markets` - Where buyers come from
- `relocation_score` - Relocation appeal score
- `investor_interest_level` - Investor interest metric
- `vacation_home_score` - Vacation home suitability
- `retirement_score` - Retirement suitability
- `family_score` - Family-friendliness score

### 16. System and Compliance Fields (12 columns)
**Description**: System tracking and regulatory compliance data
- `data_source` - Source system for listing
- `import_date` - Date imported to system
- `last_modified` - Last modification timestamp
- `modified_by` - User who last modified
- `listing_agreement_type` - Type of listing agreement
- `compensation_type` - Commission structure
- `ada_compliant` - ADA compliance flag
- `fair_housing_compliant` - Fair housing compliance
- `syndication_status` - Syndication to other sites
- `idn_status` - IDX/Internet display status
- `copyright_notice` - Copyright information
- `data_accuracy_score` - Data quality score

## Usage Notes

1. **Data Currency**: Listing data reflects the state at export time. Active listings may have changed status.

2. **Price Fields**: All price fields are in USD. Empty values indicate data not available.

3. **Agent Metrics**: Agent performance metrics are calculated from historical data in the system.

4. **Market Data**: Market profile information is updated monthly and may lag current conditions.

5. **Enrichment Sources**: Data is enriched from multiple collections including agents, brokerages, marketProfiles, usCities, listingSearch, tags, and feederMarkets.

6. **Privacy**: Some fields may contain sensitive information. Ensure proper access controls.

7. **Null Handling**: Empty fields indicate data not available or not applicable for that listing.

## Common Use Cases

- **Market Analysis**: Analyze pricing trends, inventory levels, and market dynamics
- **Agent Performance**: Track agent listing performance and specialization patterns
- **Investment Research**: Identify investment opportunities based on tags and metrics
- **Competitive Analysis**: Compare properties and understand market positioning
- **Geographic Studies**: Analyze location-based patterns and preferences
- **Marketing Insights**: Understand which features and descriptions drive engagement