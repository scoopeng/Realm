# Ultra Transaction Exporter Documentation

## Executive Summary

The Ultra Transaction Exporter provides a comprehensive view of all real estate transactions (sales) in the system with extensive enrichment from related collections. Each row represents a single transaction with 147 columns of detailed information including sale details, buyer/seller information, agent performance, property characteristics, and derived analytics. This export is designed for transaction analysis, agent performance tracking, market trend analysis, and commission calculations.

## Column Groups and Descriptions

### 1. Core Transaction Identifiers (8 columns)
**Description**: Primary transaction identification and basic sale information
- `transaction_id` - MongoDB ObjectId of the transaction
- `listing_id` - Associated listing ID
- `mls_number` - MLS number from listing
- `transaction_type` - Type of transaction (purchase, new construction, etc.)
- `status` - Transaction status (closed, pending, cancelled)
- `closing_date` - Actual closing date
- `contract_date` - Contract acceptance date
- `created_date` - Record creation date

### 2. Price and Financial Details (18 columns)
**Description**: Comprehensive pricing and financial transaction details
- `sale_price` - Final sale price
- `list_price` - Listed price at time of sale
- `original_list_price` - Original listing price
- `price_per_sqft` - Sale price per square foot
- `list_to_sale_ratio` - Percentage of list price achieved
- `seller_concessions` - Seller concession amount
- `buyer_concessions` - Buyer concession amount
- `closing_costs` - Total closing costs
- `earnest_money` - Earnest money amount
- `down_payment` - Down payment amount
- `down_payment_percent` - Down payment percentage
- `loan_amount` - Mortgage loan amount
- `loan_type` - Type of loan (conventional, FHA, VA, etc.)
- `interest_rate` - Mortgage interest rate
- `commission_total` - Total commission paid
- `listing_side_commission` - Listing side commission
- `selling_side_commission` - Selling side commission
- `transaction_sides` - Number of sides represented

### 3. Property Information (20 columns)
**Description**: Key property details at time of transaction
- `property_address` - Full property address
- `property_city` - City
- `property_state` - State
- `property_zipcode` - ZIP code
- `property_county` - County
- `property_type` - Type of property
- `year_built` - Year constructed
- `square_footage` - Total square feet
- `lot_size` - Lot size
- `bedrooms` - Number of bedrooms
- `bathrooms` - Number of bathrooms
- `garage_spaces` - Garage spaces
- `pool` - Pool (yes/no)
- `waterfront` - Waterfront property
- `hoa_fee` - HOA fee amount
- `property_condition` - Condition at sale
- `days_on_market` - Days from listing to contract
- `cumulative_dom` - Cumulative days on market
- `showing_count` - Number of showings
- `offer_count` - Number of offers received

### 4. Buyer Information (22 columns)
**Description**: Comprehensive buyer details including demographics and history
- `buyer_count` - Number of buyers
- `buyer1_id` - Primary buyer ID
- `buyer1_name` - Primary buyer name
- `buyer1_email` - Primary buyer email
- `buyer1_phone` - Primary buyer phone
- `buyer1_address` - Buyer current address
- `buyer1_city` - Buyer city
- `buyer1_state` - Buyer state
- `buyer1_zipcode` - Buyer ZIP
- `buyer2_id` - Secondary buyer ID (if applicable)
- `buyer2_name` - Secondary buyer name
- `buyer_type` - Type of buyer (individual, trust, LLC, etc.)
- `first_time_buyer` - First-time buyer flag
- `cash_buyer` - All-cash purchase flag
- `investor_buyer` - Investment purchase flag
- `out_of_state_buyer` - Out-of-state buyer flag
- `international_buyer` - International buyer flag
- `buyer_age_range` - Age range of primary buyer
- `buyer_marital_status` - Marital status
- `buyer_employment_type` - Employment type
- `buyer_previous_address` - Previous address
- `buyer_move_distance` - Distance of move in miles

### 5. Seller Information (20 columns)
**Description**: Comprehensive seller details including ownership history
- `seller_count` - Number of sellers
- `seller1_id` - Primary seller ID
- `seller1_name` - Primary seller name
- `seller1_email` - Primary seller email
- `seller1_phone` - Primary seller phone
- `seller1_address` - Seller forwarding address
- `seller1_city` - Seller new city
- `seller1_state` - Seller new state
- `seller1_zipcode` - Seller new ZIP
- `seller2_id` - Secondary seller ID (if applicable)
- `seller2_name` - Secondary seller name
- `seller_type` - Type of seller (individual, estate, bank, etc.)
- `ownership_duration` - Years of ownership
- `seller_profit` - Calculated profit/loss
- `seller_original_purchase_price` - Original purchase price
- `seller_original_purchase_date` - Original purchase date
- `relocation_seller` - Corporate relocation flag
- `estate_sale` - Estate sale flag
- `foreclosure_sale` - Foreclosure/REO flag
- `short_sale` - Short sale flag

### 6. Agent Information - Listing Side (15 columns)
**Description**: Listing agent details and performance metrics
- `listing_agent_id` - Listing agent ID
- `listing_agent_name` - Listing agent full name
- `listing_agent_email` - Listing agent email
- `listing_agent_phone` - Listing agent phone
- `listing_agent_license` - License number
- `listing_agent_experience` - Years of experience
- `listing_office_id` - Listing office ID
- `listing_office_name` - Listing office name
- `listing_brokerage_id` - Listing brokerage ID
- `listing_brokerage_name` - Listing brokerage name
- `listing_agent_commission` - Commission amount
- `listing_agent_commission_rate` - Commission rate
- `listing_agent_total_sales` - Agent's total career sales
- `listing_agent_annual_sales` - Agent's annual sales
- `listing_agent_ranking` - Agent market ranking

### 7. Agent Information - Selling Side (15 columns)
**Description**: Selling (buyer's) agent details and performance metrics
- `selling_agent_id` - Selling agent ID
- `selling_agent_name` - Selling agent full name
- `selling_agent_email` - Selling agent email
- `selling_agent_phone` - Selling agent phone
- `selling_agent_license` - License number
- `selling_agent_experience` - Years of experience
- `selling_office_id` - Selling office ID
- `selling_office_name` - Selling office name
- `selling_brokerage_id` - Selling brokerage ID
- `selling_brokerage_name` - Selling brokerage name
- `selling_agent_commission` - Commission amount
- `selling_agent_commission_rate` - Commission rate
- `selling_agent_total_sales` - Agent's total career sales
- `selling_agent_annual_sales` - Agent's annual sales
- `selling_agent_ranking` - Agent market ranking

### 8. Transaction Process Details (12 columns)
**Description**: Timeline and process information for the transaction
- `offer_date` - Initial offer date
- `negotiation_days` - Days in negotiation
- `inspection_date` - Property inspection date
- `inspection_issues` - Inspection issue flag
- `appraisal_date` - Property appraisal date
- `appraisal_value` - Appraised value
- `appraisal_variance` - Variance from sale price
- `contingencies` - Active contingencies
- `contingency_removal_date` - Contingency removal date
- `escrow_company` - Escrow company name
- `title_company` - Title company name
- `home_warranty` - Home warranty included

### 9. Market Context (10 columns)
**Description**: Market conditions at time of transaction
- `market_temperature` - Hot/warm/cool/cold market
- `inventory_level` - Months of inventory
- `median_area_price` - Area median price
- `price_vs_median` - Sale price vs median percentage
- `similar_sales_count` - Comparable sales count
- `market_trend` - Rising/stable/falling
- `seasonal_factor` - Seasonal adjustment factor
- `competition_level` - Market competition score
- `negotiation_leverage` - Buyer/seller leverage indicator
- `multiple_offers` - Multiple offer situation flag

### 10. Derived Analytics (15 columns)
**Description**: Calculated metrics and insights from transaction data
- `price_trajectory` - Price trend for property type
- `agent_match_score` - Agent-client fit score
- `transaction_complexity` - Complexity score
- `speed_of_sale` - Fast/normal/slow sale indicator
- `pricing_accuracy` - List price accuracy score
- `negotiation_effectiveness` - Negotiation success score
- `buyer_preparedness_score` - Buyer readiness score
- `transaction_smoothness` - Process smoothness score
- `commission_efficiency` - Commission vs effort ratio
- `market_timing_score` - Market timing effectiveness
- `repeat_client_transaction` - Repeat client flag
- `referral_transaction` - Referral-based flag
- `dual_agency` - Dual agency flag
- `commission_variance` - Variance from standard commission
- `profitability_score` - Transaction profitability score

### 11. Client Relationship Data (12 columns)
**Description**: Historical client relationship and engagement information
- `buyer_previous_transactions` - Buyer's previous transaction count
- `buyer_first_transaction_date` - Buyer's first transaction
- `buyer_agent_loyalty_score` - Buyer-agent loyalty metric
- `seller_previous_transactions` - Seller's previous transaction count
- `seller_first_transaction_date` - Seller's first transaction
- `seller_agent_loyalty_score` - Seller-agent loyalty metric
- `client_satisfaction_score` - Post-transaction satisfaction
- `client_referral_likelihood` - Referral likelihood score
- `agent_client_communication_score` - Communication effectiveness
- `client_lifetime_value` - Estimated client lifetime value
- `follow_up_engagement` - Post-sale engagement level
- `anniversary_contact` - Anniversary follow-up flag

### 12. Residence History Integration (10 columns)
**Description**: Buyer and seller residence history patterns
- `buyer_residence_count` - Number of previous residences
- `buyer_avg_residence_duration` - Average time at residence
- `buyer_residence_pattern` - Move pattern (upgrading/downsizing/lateral)
- `buyer_geographic_pattern` - Geographic movement pattern
- `seller_residence_count` - Seller's residence history count
- `seller_next_purchase_likelihood` - Likelihood of next purchase
- `seller_next_purchase_timeframe` - Estimated timeframe
- `chain_transaction` - Part of transaction chain flag
- `contingent_sale` - Contingent on sale flag
- `simultaneous_closing` - Simultaneous closing flag

### 13. System and Compliance Fields (8 columns)
**Description**: System tracking and regulatory compliance data
- `data_source` - Source system
- `import_date` - Import timestamp
- `last_modified` - Last modification date
- `modified_by` - User who modified
- `compliance_review_date` - Compliance review date
- `audit_status` - Audit status
- `document_status` - Document completion status
- `archive_date` - Archive date for closed transactions

## Usage Notes

1. **Data Privacy**: Transaction data contains sensitive financial and personal information. Ensure proper access controls and data handling procedures.

2. **Commission Calculations**: Commission fields reflect actual paid amounts, which may vary from standard rates due to negotiations or special arrangements.

3. **Derived Metrics**: Analytical scores and derived metrics are calculated using proprietary algorithms and historical data patterns.

4. **Date Fields**: All dates are in YYYY-MM-DD format. Empty dates indicate information not available.

5. **Price Fields**: All monetary values are in USD. Negative values in profit fields indicate losses.

6. **Enrichment Sources**: Data is enriched from agents, brokerages, people, agentclients, residences, and transactionsderived collections.

7. **Historical Data**: Previous transaction information relies on data availability in the system and may not capture transactions outside the system.

## Common Use Cases

- **Agent Performance Analysis**: Track agent productivity, commission earnings, and client satisfaction
- **Market Trend Analysis**: Analyze pricing trends, days on market, and seasonal patterns
- **Commission Analysis**: Understand commission structures and variations
- **Client Behavior Studies**: Analyze buyer and seller patterns, repeat business, and referrals
- **Transaction Efficiency**: Identify bottlenecks and optimize transaction processes
- **Predictive Analytics**: Build models for pricing, time-to-close, and client lifetime value
- **Brokerage Performance**: Compare office and company performance metrics
- **Investment Analysis**: Track investor activity and returns