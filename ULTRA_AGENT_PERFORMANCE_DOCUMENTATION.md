# Ultra Agent Performance Exporter Documentation

## Executive Summary

The Ultra Agent Performance Exporter provides a comprehensive 360-degree view of real estate agent performance, capabilities, and business metrics. Each row represents a single agent with 156 columns of detailed information including personal details, professional credentials, performance metrics, team affiliations, client engagement, awards, and market presence. This export is designed for agent recruiting, performance management, market analysis, training needs assessment, and competitive intelligence.

## Column Groups and Descriptions

### 1. Agent Basic Information (9 columns)
**Description**: Core agent identification and contact details
- `agent_id` - MongoDB ObjectId of the agent
- `agent_full_name` - Agent's complete name
- `agent_first_name` - First name
- `agent_last_name` - Last name
- `agent_email` - Primary email address
- `agent_phone` - Primary phone number
- `agent_mobile` - Mobile phone number
- `agent_office_phone` - Office phone number
- `agent_fax` - Fax number

### 2. Professional Credentials (9 columns)
**Description**: Licensing and professional qualification information
- `agent_license_number` - Real estate license number
- `agent_license_state` - State of licensure
- `agent_years_experience` - Years in real estate
- `agent_start_date` - Career start date
- `agent_website` - Personal website URL
- `agent_social_media` - Social media profiles
- `agent_bio_length` - Biography word count
- `agent_photo_url` - Professional photo URL
- `agent_license_status` - Active/inactive license status

### 3. Professional Designations (17 columns)
**Description**: Industry certifications and designations as binary indicators
- `designation_ABR` - Accredited Buyer's Representative
- `designation_CRS` - Certified Residential Specialist
- `designation_GRI` - Graduate, REALTOR® Institute
- `designation_SRES` - Senior Real Estate Specialist
- `designation_SRS` - Seller Representative Specialist
- `designation_PSA` - Pricing Strategy Advisor
- `designation_RENE` - Real Estate Negotiation Expert
- `designation_MRP` - Military Relocation Professional
- `designation_ALC` - Accredited Land Consultant
- `designation_GREEN` - Green certification
- `designation_ePRO` - Digital marketing certification
- `designation_RSPS` - Resort & Second-Home Property Specialist
- `designation_SFR` - Short Sales & Foreclosure Resource
- `designation_AHWD` - At Home With Diversity
- `designation_ASP` - Accredited Staging Professional
- `designation_BPOR` - Broker Price Opinion Resource
- `designation_CCIM` - Certified Commercial Investment Member

### 4. Specialization Areas (20 columns)
**Description**: Agent specialty areas as binary indicators
- `specialty_buyer_agent` - Buyer representation specialty
- `specialty_seller_agent` - Seller representation specialty
- `specialty_luxury` - Luxury properties
- `specialty_first_time_buyers` - First-time buyer expertise
- `specialty_investors` - Investment properties
- `specialty_relocation` - Relocation services
- `specialty_foreclosures` - Foreclosure properties
- `specialty_short_sales` - Short sale expertise
- `specialty_commercial` - Commercial real estate
- `specialty_residential` - Residential focus
- `specialty_new_construction` - New construction
- `specialty_condos` - Condominium specialist
- `specialty_townhomes` - Townhome specialist
- `specialty_single_family` - Single family homes
- `specialty_multi_family` - Multi-family properties
- `specialty_waterfront` - Waterfront properties
- `specialty_golf_communities` - Golf community specialist
- `specialty_55_plus` - Senior communities
- `specialty_vacation_homes` - Vacation properties
- `specialty_land` - Land and lots

### 5. Personal Information (8 columns)
**Description**: Agent personal details and demographics
- `person_id` - Person record ID
- `person_address` - Home address
- `person_city` - City of residence
- `person_state` - State of residence
- `person_zipcode` - ZIP code
- `person_county` - County of residence
- `person_languages` - Languages spoken
- `person_education` - Education background

### 6. Brokerage Affiliation (12 columns)
**Description**: Current brokerage relationship details
- `brokerage_id` - Brokerage identifier
- `brokerage_name` - Brokerage company name
- `brokerage_phone` - Brokerage main phone
- `brokerage_email` - Brokerage email
- `brokerage_website` - Company website
- `brokerage_address` - Office address
- `brokerage_city` - Office city
- `brokerage_state` - Office state
- `brokerage_zipcode` - Office ZIP code
- `brokerage_type` - Type of brokerage
- `brokerage_size` - Company size category
- `brokerage_year_established` - Year founded

### 7. Listing Performance Metrics (13 columns)
**Description**: Agent's listing activity and performance
- `total_listings` - Career total listings
- `active_listings` - Current active listings
- `pending_listings` - Current pending listings
- `sold_listings` - Total sold listings
- `expired_listings` - Expired listing count
- `withdrawn_listings` - Withdrawn listing count
- `average_list_price` - Average listing price
- `median_list_price` - Median listing price
- `total_listing_volume` - Total dollar volume listed
- `average_days_on_market` - Average DOM for listings
- `listings_last_30_days` - Recent listing activity
- `listings_last_90_days` - Quarterly listing activity
- `listings_last_year` - Annual listing activity

### 8. Sales Performance Metrics (11 columns)
**Description**: Agent's sales transactions and results
- `total_sales` - Career total sales
- `buyer_sales` - Sales representing buyers
- `seller_sales` - Sales representing sellers
- `dual_agency_sales` - Dual agency transactions
- `average_sale_price` - Average sale price
- `median_sale_price` - Median sale price
- `total_sales_volume` - Total sales dollar volume
- `average_sale_to_list_ratio` - Average sale-to-list percentage
- `sales_last_30_days` - Recent sales activity
- `sales_last_90_days` - Quarterly sales activity
- `sales_last_year` - Annual sales activity

### 9. Property Type Breakdown (8 columns)
**Description**: Sales distribution by property type
- `single_family_sales` - Single family home sales
- `condo_sales` - Condominium sales
- `townhome_sales` - Townhome sales
- `multi_family_sales` - Multi-family sales
- `land_sales` - Land/lot sales
- `commercial_sales` - Commercial property sales
- `luxury_sales_count` - Luxury property sales
- `first_time_buyer_sales` - First-time buyer transactions

### 10. Geographic Coverage (5 columns)
**Description**: Agent's geographic market presence
- `cities_served_count` - Number of cities served
- `primary_city` - Primary market city
- `primary_zipcode` - Primary market ZIP
- `zipcodes_served_count` - Total ZIP codes served
- `counties_served_count` - Total counties served

### 11. Price Range Expertise (6 columns)
**Description**: Sales distribution by price ranges
- `under_200k_sales` - Sales under $200K
- `200k_400k_sales` - Sales $200K-$400K
- `400k_600k_sales` - Sales $400K-$600K
- `600k_800k_sales` - Sales $600K-$800K
- `800k_1m_sales` - Sales $800K-$1M
- `over_1m_sales` - Sales over $1M

### 12. Client Metrics (5 columns)
**Description**: Client relationship and satisfaction metrics
- `repeat_client_rate` - Percentage of repeat clients
- `referral_rate` - Percentage from referrals
- `average_commission_rate` - Average commission percentage
- `total_commission_earned` - Career commission earnings
- `average_transaction_sides` - Average sides per transaction

### 13. Market Position (5 columns)
**Description**: Agent's market standing and rankings
- `market_share_listings` - Listing market share
- `market_share_sales` - Sales market share
- `brokerage_rank` - Rank within brokerage
- `city_rank` - Rank within primary city
- `production_tier` - Production level tier

### 14. Activity and Engagement (5 columns)
**Description**: Recent activity and market engagement
- `last_listing_date` - Most recent listing date
- `last_sale_date` - Most recent sale date
- `months_since_last_activity` - Inactivity period
- `listing_to_sale_conversion_rate` - Conversion percentage
- `average_price_reduction` - Average price adjustment

### 15. Team Information (8 columns)
**Description**: Team membership and structure details
- `is_team_member` - Team member flag
- `team_count` - Number of teams affiliated with
- `primary_team_name` - Primary team name
- `primary_team_id` - Primary team identifier
- `team_size` - Number of team members
- `team_role` - Role within team (leader/member)
- `team_total_agents` - Total agents on team
- `team_founded_date` - Team establishment date

### 16. Search Visibility and Marketing (5 columns)
**Description**: Online presence and marketing effectiveness
- `search_visibility_score` - Search result visibility
- `profile_completeness_score` - Profile completion percentage
- `search_ranking_position` - Search result position
- `profile_views_count` - Profile view count
- `contact_requests_count` - Inquiry count

### 17. Client Engagement Events (6 columns)
**Description**: Client interaction and event participation
- `total_client_events` - Total client events
- `avg_events_per_client` - Average events per client
- `unique_clients_count` - Unique client count
- `client_retention_rate` - Client retention percentage
- `event_participation_rate` - Event participation rate
- `last_client_event_date` - Most recent client event

### 18. Awards and Recognition (5 columns)
**Description**: Professional awards and achievements
- `total_awards_count` - Total awards received
- `award_categories` - Award category list
- `most_recent_award` - Latest award received
- `top_producer_years` - Years as top producer
- `industry_recognition_score` - Recognition score

## Usage Notes

1. **Performance Metrics**: All performance metrics are calculated from transaction data within the system and may not reflect activity outside the platform.

2. **Designation Fields**: Designation indicators are binary (true/false) based on text parsing of agent profiles.

3. **Team Information**: Agents may belong to multiple teams; primary team is determined by first membership or explicit designation.

4. **Geographic Data**: Geographic coverage is derived from transaction locations and may not reflect full service areas.

5. **Commission Data**: Commission information is aggregated and anonymized for privacy.

6. **Ranking Metrics**: Rankings are calculated relative to other agents in the same market area.

7. **Time-Based Metrics**: Recent activity metrics (30/90/365 days) are calculated from the export date.

8. **Client Events**: Event data comes from the agentclientevents collection and may not capture all client interactions.

## Common Use Cases

- **Recruiting**: Identify top-performing agents for recruitment
- **Performance Management**: Track agent productivity and identify training needs
- **Market Analysis**: Understand agent distribution and specializations by market
- **Commission Planning**: Analyze commission structures and earnings
- **Team Building**: Identify successful team structures and compositions
- **Training Programs**: Identify agents who would benefit from specific training
- **Succession Planning**: Identify agents nearing retirement or reducing activity
- **Marketing Investment**: Allocate marketing resources based on agent performance
- **Competitive Intelligence**: Understand competitor agent strength by market
- **Retention Programs**: Identify at-risk agents based on activity patterns