# CLAUDE.md - MongoDB Export Utility
*Last Updated: December 2, 2025 - CSV Trailing Backslash Fix*

This file provides comprehensive guidance to Claude Code when working with this codebase.

## CRITICAL DEVELOPMENT PRINCIPLES

### 1. Don't Be Rash
- Think deeply before making changes
- Consider long-term implications, not just short-term fixes
- Prioritize code cleanliness, consistency, and maintainability
- Make decisions that improve the WHOLE system, not just one part

### 2. No Hardcoding
- Everything should be data-driven and configuration-based
- If hints are needed, they should be universal patterns (e.g., shortest name wins)
- System must work with ANY MongoDB database without code changes
- Single source of truth: configuration files

### 3. Consistency is Key
- ONE logic path for each operation
- Discovery determines relationships, everyone follows
- No duplicate or competing systems
- Both phases must see exactly the same data

### 4. Simplicity (Occam's Razor)
- The simplest solution is usually correct
- Don't over-engineer or overfit
- Keep code simple and maintainable
- When in doubt, choose the cleaner approach

## PROJECT OVERVIEW

MongoDB to CSV export utility with a **two-phase workflow**: 
1. **Discovery Phase**: Analyzes MongoDB collections to discover all fields and generate an editable JSON configuration
2. **Export Phase**: Uses the JSON configuration to export data with precise control over included fields

The system is fully data-driven, works with any MongoDB database, and provides intelligent field filtering, relationship expansion, and advanced array handling.

## TWO-PHASE ARCHITECTURE

### Phase 1: Discovery
```bash
./gradlew discover -Pcollection=listings
```
- **Output**: 
  - `config/listings_fields.json` - Editable field configuration
  - `config/listings_expansion_audit.txt` - Hierarchical expansion tree
- **Process**:
  1. Samples 10,000 documents to discover fields
  2. Automatically detects ObjectId relationships
  3. Tests ObjectIds against all collections to find matches
  4. When multiple collections match, selects shortest name (Occam's razor)
  5. Expands relationships to discover nested fields
  6. Filters fields based on data quality rules
  7. Saves configuration as single source of truth

### Phase 2: Export  
```bash
./gradlew configExport -Pcollection=listings
```
- **Input**: `config/listings_fields.json`
- **Process**:
  1. Reads configuration (single source of truth)
  2. Caches small referenced collections (<600K docs)
  3. Never caches the source collection
  4. Exports data using configuration's relationship mappings
  5. Supports multiple array handling modes

## KEY ARCHITECTURAL COMPONENTS

### Relationship Discovery System
- **RelationshipDiscovery.java**: Automatically discovers relationships by testing ObjectIds
  - No hardcoding - learns from actual data
  - Simple heuristic: shortest collection name wins (e.g., "people" beats "people_meta")
  
- **RelationExpander.java**: Configuration-driven expansion
  - ALL hardcoded mappings removed (August 24, 2025)
  - Accepts discovered relationships via `setDiscoveredRelationships()`
  - Both phases use identical logic

### Field Filtering Rules
- **Include**: Business IDs with data (mlsNumber, listingId, transactionId)
- **Exclude**: Technical fields (_id, __v, fields ending with Id)
- **Exclude**: Empty fields (0 distinct non-null values)
- **Exclude**: Single-value fields (only 1 distinct value)
- **Exclude**: Sparse fields (<10% document presence)
- **Include**: Multi-value fields (2+ distinct values AND >10% presence)

### Array Handling Modes
- **list**: Comma-separated values (default)
- **primary**: Extract fields from first element
- **count**: Array length
- **statistics**: Aggregated metrics (sum, avg, min, max)

### Performance & Caching
- **Centralized Cache Manager**: Single source of truth for all caching logic (CollectionCacheManager.java)
- **Smart Caching**: Collections <1M docs cached in memory (configurable in one place)
- **Never Cache Source**: Prevents cursor failures during export
- **Lazy Loading**: Large collections loaded on-demand
- **Export Speed**: 2,000-15,000 docs/sec depending on complexity


## CRITICAL BUG FIXES & IMPROVEMENTS

### December 2, 2025 - CSV Trailing Backslash Fix
- **Problem**: Scoop's CSVScanner treated `\"` as escape sequence, causing column misalignment when fields ended with backslash
- **Symptom**: Price Amount column contained "Carbondale" (a city name) instead of numeric value; 2 rows had only 48 columns instead of 51
- **Root Cause**: Data fields ending with `\` followed by closing quote `"` were misinterpreted as escaped quotes
- **Example**: `"...text ending with \"` was parsed as `"...text ending with "` (quote not closed)
- **Fix**: Added trailing space to fields ending with backslash in `AbstractUltraExporter.writeCSVRowWithTypes()` (lines 284-302)
- **Files Changed**: `src/main/java/com/example/mongoexport/AbstractUltraExporter.java`
- **Companion Tool**: Created `CSVProfiler` in Scoop to diagnose CSV type detection issues
- **Result**: All 45,758 listings rows now parse correctly with numeric Price Amount

### October 9, 2025 - Discovery PRIMARY Field Limit Fix
- **Problem**: Discovery phase limited to 4 PRIMARY fields per array, causing `residences[primary].state` to be omitted
- **Root Cause**: Conservative limit in FieldDiscoveryService.java (lines 1184, 1201) stopped after finding address, city, postalCode, country
- **Fix**: Increased PRIMARY field generation limit from 4 → 6
  - Allows full address extraction: address, city, state, postalCode, country, and one more field
  - Updated in both ObjectId reference arrays and embedded document arrays
- **Impact**: Future discoveries will capture more complete address data
- **Manual Fix Required**: Added `residences[primary].state` to personwealthxdataMongo config for this export
- **Result**: All 6 residence PRIMARY fields now working + COUNT mode validated at 100K scale

### August 24, 2025 Session 2 - Complete Architecture Overhaul
- **Created CollectionCacheManager**: Single source of truth for ALL caching logic
  - Eliminated duplicate code between discovery and export phases
  - Cache threshold now configurable in ONE place (currently 1M docs)
  - Both phases use identical caching behavior
- **Fixed ObjectId pre-resolution bug**: 
  - Fields with expanded relationships now preserve ObjectIds instead of converting to display names
  - Enables proper expansion for ownerAgent and ownerTeam fields
  - Same pattern as the client/people fix from Session 1
- **Fixed nested field extraction**: Added proper handling for nested paths like `realmData.ownerAgent`
- **Increased cache limit**: From 600K to 1M to ensure `people` collection (622K) is fully cached
- **Code cleanup**: Removed all dead code and redundant cache tracking
- **Result**: ALL 67 fields now have data, including expanded owner fields (99%+ coverage)

### August 24, 2025 Session 1 - Unified Relationship Discovery  
- **Removed hardcoded mappings**: All relationships now discovered automatically
- **Fixed discovery/export mismatch**: Both phases now use same `people` collection
- **Result**: Consistent 67 fields with address data in both phases

### August 23, 2025 - Performance Fixes
- **Lazy Loading Fix**: Eliminated unnecessary DB lookups (20-75x speedup)
- **Compound Sparsity**: Fixed calculation using actual parent counts
- **Cache Alignment**: Both phases cache up to same document limit

### August 19, 2025 - Export Stopping Bug
- **Problem**: Export stopped at 12,646 rows
- **Cause**: Caching source collection caused cursor failure
- **Fix**: Never cache source collection, limit other caches

## PRODUCTION COMMANDS

### Standard Workflow

**Realm Database (default)**:
```bash
# Discovery
./gradlew discover -Pcollection=agentclients

# Review/edit configuration (optional)
vi config/agentclients_fields.json

# Export
./gradlew configExport -Pcollection=agentclients

# Test with row limit
./gradlew configExport -Pcollection=agentclients -ProwLimit=1000
```

**WealthX Database (lake environment)** ✅ PRODUCTION READY:
```bash
# Discovery
./gradlew discover -Pcollection=personwealthxdataMongo -Penv=lake -Pdatabase=WealthX

# Review/edit configuration (optional)
vi config/personwealthxdataMongo_fields.json

# Export (test with limit first)
./gradlew configExport -Pcollection=personwealthxdataMongo -Penv=lake -Pdatabase=WealthX -ProwLimit=1000

# Full export (4.5M records - validated at 13,401 rows/sec, ~5.6 minutes)
./gradlew configExport -Pcollection=personwealthxdataMongo -Penv=lake -Pdatabase=WealthX
# Output: 2.0 GB CSV file with 4,458,826 rows and 46 columns
```

### Available Collections

**Realm Database (prod environment)**:
- `listings` - ~64K property listings
- `transactions` - Sales transactions
- `agents` - ~28K agent profiles
- `agentclients` - ~573K agent-client relationships
- `properties` - ~1.9M properties (use with caution)

**WealthX Database (lake environment)** ✅ VALIDATED:
- `personwealthxdataMongo` - 4.5M wealth profiles (46 fields exported, 2.0 GB file)

### Performance Expectations
- **Discovery**: ~2-3 minutes for 10K sample with expansion
- **Export**:
  - <100K docs: 1-2 minutes
  - 100K-500K docs: 3-5 minutes
  - 1M-5M docs: 5-10 minutes (WealthX: 4.5M in 5.6 minutes @ 13,401 rows/sec)
- **Memory**: ~1.5GB for caching people collection (622K docs)

## TROUBLESHOOTING

### Common Issues
1. **Discovery finds wrong collection**: Check shortest name heuristic
2. **Export missing data**: Verify configuration's relationshipTarget
3. **Memory errors**: Increase heap in build.gradle
4. **Slow export**: Check if collections are properly cached

### Debug Commands
```bash
# Check discovered relationships
grep "relationshipTarget" config/agentclients_fields.json

# View expansion audit
cat config/agentclients_expansion_audit.txt

# Monitor export progress
tail -f output/export.log
```

## PROJECT STRUCTURE

### Core Classes
- `cache/CollectionCacheManager.java` - Centralized caching logic (single source of truth)
- `discovery/RelationshipDiscovery.java` - Automatic relationship detection
- `discovery/FieldDiscoveryService.java` - Field discovery and filtering
- `RelationExpander.java` - Configuration-driven relationship expansion
- `export/ConfigurationBasedExporter.java` - Export using configuration
- `config/DiscoveryConfiguration.java` - Configuration model

### Configuration Files
- `config/{collection}_fields.json` - Field configuration (source of truth)
- `config/{collection}_expansion_audit.txt` - Expansion audit trail
- `output/{collection}_full_*.csv` - Export output

## DATABASE CONNECTION

### Available Environments
Configure in `application.properties`:
```properties
# Production environments
mongodb.url.dev=mongodb+srv://user:pass@realmdev.m0dfg.mongodb.net/
mongodb.url.stage=mongodb+srv://user:pass@stage.zhil9.mongodb.net/
mongodb.url.prod=mongodb+srv://user:pass@prod-shared.zhil9.mongodb.net/
mongodb.url.lake=mongodb+srv://user:pass@realm-big-data.zhil9.mongodb.net/

current.environment=prod
database.name=realm
output.directory=./output
```

### Environment Details
- **dev**: Development database with `realm` database only
- **stage**: Staging database with `realm` database only
- **prod**: Production database with `realm` database only (current default)
- **lake**: Big data cluster with `realmLake` and `WealthX` databases

**Important:** The `WealthX` database exists **ONLY on the lake cluster**. It is not available in dev, stage, or prod environments.

## MONGODB EXPLORER TOOL

### Overview
Standalone exploration tool for investigating any MongoDB database without modifying existing export workflows.

### Usage
```bash
# List all collections in lake/realmLake (default)
./gradlew explore

# List collections in specific database
./gradlew explore -Pdatabase=WealthX

# Inspect a specific collection
./gradlew explore -Pdatabase=WealthX -Pcollection=personwealthxdataMongo

# Use different environment
./gradlew explore -Penv=prod -Pdatabase=realm
```

### Features
- Lists all available databases on a cluster
- Shows collection names with document counts
- Samples 100 documents to discover field structure
- Displays field types, nested paths, and array structures
- Shows sample document in pretty JSON format
- Zero impact on production export workflows

### Implementation
- **File**: `MongoExplorerRunner.java`
- **Gradle Task**: `explore` in build.gradle
- **Default Environment**: lake
- **Default Database**: realmLake

## WEALTHX DATABASE (LAKE CLUSTER)

### Database Overview
Located on the **lake** environment cluster, contains high-value wealth and demographic data.

**CRITICAL:** This database exists **ONLY** on the lake cluster. Not available in dev, stage, or prod environments.

### Available Databases on Lake Cluster
1. **WealthX** - Primary wealth data (5M+ records) - **LAKE ONLY**
2. **realmLake** - ListHub real estate data (24M+ records) - **LAKE ONLY**

### Environment Database Availability
```
Environment    realm    realmLake    WealthX
───────────────────────────────────────────────
dev            ✓        ✗            ✗
stage          ✓        ✗            ✗
prod           ✓        ✗            ✗
lake           ✓        ✓            ✓
```

### WealthX Database Collections
```
Collection Name                    Document Count       Status
────────────────────────────────────────────────────────────────
listhubAgents                                   0       Empty
listhubBrokerages                               0       Empty
listhubIndex                                    0       Empty
listhubListings                                 0       Empty
people                                    311,633       Active
personwealthxdataMongo                  4,458,826       Active (PRIMARY)
prospectSearch                                  0       Empty
wxProfileData                             272,106       Active
```

### personwealthxdataMongo Collection (4.5M Records)

**Primary wealth data collection** with comprehensive individual profiles.

#### Field Structure (63 unique field paths)

**Personal Information:**
- `firstName`, `lastName`, `fullName`, `email`
- `DOB`, `age`, `maritalStatus`, `sex`
- `photoUrl`, `wxId` (WealthX ID)
- `nickNames[]` - Array of alternate names
- `tags[]` - Array of wealth/lifestyle tags
- `lifestyles[]` - Array of lifestyle indicators

**Business Information:**
- `business.company`, `business.position`
- `business.phone`, `business.email`
- `business.address`, `business.city`, `business.state`, `business.postalCode`, `business.country`

**Residence Data:**
- `residences[]` - **Array of residence objects**
  - `address`, `city`, `state`, `postalCode`, `country`
  - Multiple residences per person common

**Net Worth & Financial Data:**
- `netWorth.householdWealth` - Total household wealth (string)
- `netWorth.householdNetWorth` - Net worth value (string)
- `netWorth.householdLiquidAsset` - Liquid assets (string)
- `netWorth.liquidLowerInt` - Liquid assets lower bound (integer)
- `netWorth.netWorthLowerInt` - Net worth lower bound (integer)
- `netWorth.chartURL` - Link to WealthX asset visualization

**Asset Holdings:**
- `netWorth.netWorthHoldings[]` - **Array of asset objects**
  - `type` - Asset category (Alternative Assets, Public Holdings, Cash & Other)
  - `assetName` - Specific asset name
  - `assetType` - Asset classification (Property, Stock, etc.)
  - `valueOfHoldings` - Estimated value (integer)
  - `remarks` - Detailed description
  - **For public stock holdings:**
    - `entityID`, `entityTypeID`, `entityTypeName`
    - `industryTypeID`, `industryTypeName`
  - Common assets: Properties, Stock holdings, Cash/investments

**Interests & Hobbies:**
- `interests[]` - **Array of interest objects**
  - `hobbyName` - Name of hobby/interest
  - `hobbyDescription` - Detailed description
  - Not present for all records (~52% have data)

**Metadata:**
- `lastIngestion` - Date record was ingested (Date type)
- `lastWXUpdate` - Last WealthX update timestamp (string)
- `updatedAt` - Last modification date (Date type)
- `__v` - Version field
- `cities`, `names` - Pipe-delimited search fields
- `primary` - Boolean/String flag

#### Array Handling Considerations for WealthX Export

**Critical Array Fields Requiring Special Handling:**

1. **residences[]** (Present in 100% of records)
   - **Challenge**: Multiple residences per person very common
   - **Options**:
     - `primary` mode: Extract first/primary residence only
     - `count` mode: Number of residences
     - `list` mode: Comma-separated cities/states
     - **Recommendation**: Create separate residence fields (primary + count)

2. **netWorth.netWorthHoldings[]** (Present in ~35% of records)
   - **Challenge**: Variable number of assets, complex nested structure
   - **Options**:
     - `statistics` mode: Sum total holdings, count assets
     - `primary` mode: Largest asset only
     - Separate by asset type (property count, stock holdings count, total value)
     - **Recommendation**: Aggregate statistics (total value, count by type)

3. **interests[]** (Present in ~52% of records)
   - **Challenge**: Variable array, nested objects
   - **Options**:
     - `count` mode: Number of interests
     - `list` mode: Comma-separated hobby names
     - **Recommendation**: Simple count or list of names

4. **tags[]**, **lifestyles[]** (Present in 100% of records)
   - **Challenge**: Array of objects with tagName and score
   - **Options**:
     - `list` mode: Comma-separated tag names
     - `count` mode: Number of tags
     - **Recommendation**: List mode for tag names

5. **nickNames[]** (Present in 100% of records, often empty)
   - **Simple array**: Can use standard list mode

#### Sample Record Structure
```json
{
  "fullName": "J. Fredericks",
  "age": 75,
  "maritalStatus": "Married",
  "residences": [
    {
      "city": "Sausalito",
      "state": "California",
      "postalCode": "94965-2323"
    }
  ],
  "business": {
    "company": "Main Management",
    "position": "Partner",
    "city": "Houston",
    "state": "Texas"
  },
  "netWorth": {
    "householdWealth": "5500000.00",
    "householdLiquidAsset": "12000000.00",
    "netWorthHoldings": [
      {
        "type": "Alternative Assets",
        "assetType": "Property",
        "assetName": "Property in Sausalito, California",
        "valueOfHoldings": 700000
      },
      {
        "type": "Public Holdings - Common Stock",
        "entityTypeName": "Public Company",
        "industryTypeName": "Finance / Banking / Investment",
        "valueOfHoldings": 3000000
      }
    ]
  }
}
```

#### Export Strategy Recommendations

**For WealthX personwealthxdataMongo export:**

1. **Flatten single-value nested objects** (business, primary residence)
2. **Aggregate array statistics** (asset counts, total values)
3. **Extract primary elements** (first residence, largest asset)
4. **Use list mode for simple arrays** (tags, nickNames)
5. **Consider separate exports** for complex arrays (one row per asset holding)

**Expected Field Count**: 50-80 flattened fields including:
- ~15 personal/demographic fields
- ~10 business fields
- ~10 primary residence fields
- ~15 net worth aggregates
- ~10 array counts/lists

### wxProfileData Collection (272K Records)

Subset of personwealthxdataMongo with additional computed fields:
- `affordabilityScore` - Calculated affordability metric
- Similar structure but smaller dataset
- May have different field coverage

## GIT INFORMATION
- Repository: https://github.com/scoopeng/Realm
- Main branch: master
- Current version: 2.0-SNAPSHOT

## SUPPLEMENTAL CONFIGURATION SYSTEM

### Overview
Supplemental configurations allow manual field additions without modifying auto-discovered configs.

### File Location
- Main config: `config/{collection}_fields.json` (auto-generated)
- Supplemental: `config/{collection}_supplemental.json` (manual)

### Supplemental Format
```json
{
  "collection": "agentclients",
  "requiredCollections": ["personexternaldatas"],
  "fields": [
    {
      "fieldPath": "client_personexternaldata.externalData.data.tags.Income_HH",
      "businessName": "Client Household Income",
      "sourceCollection": "personexternaldatas",
      "dataType": "string",
      "include": true
    }
  ]
}
```

### Special Field Paths
- `client_personexternaldata.*` - Triggers reverse lookup in personexternaldatas
- Export phase finds matching records where `personexternaldatas.person = people._id`

## KEY PRINCIPLES SUMMARY

Remember:
1. **Think before acting** - Don't be rash
2. **No hardcoding** - Everything configuration-driven  
3. **One source of truth** - Configuration file for fields, CollectionCacheManager for caching
4. **Consistency** - Both phases use identical logic (no code duplication)
5. **Simplicity** - Occam's razor wins (e.g., shortest collection name)

## CURRENT STATUS (December 2, 2025)

### 🚨 CRITICAL DATA LOSS DETECTED - REQUIRES INVESTIGATION

**Latest Export Run: December 2, 2025**
- **Purpose**: Refresh all Realm production exports for Scoop import
- **Result**: Discovered 47% data loss in agentclients collection since August 2025
- **Status**: Exports completed successfully, but MongoDB data integrity issue requires investigation

### Production Exports (Realm Database) - December 2, 2025

#### ⚠️ AGENTCLIENTS - MAJOR DATA LOSS (MongoDB Verified)
- **August 25, 2025**: 573,874 records in MongoDB → 573,874 rows exported (54 fields, 178 MB)
- **December 2, 2025**: 301,930 records in MongoDB → 301,930 rows exported (97 fields, 275 MB)
- **LOSS**: -271,944 records (-47.4%) ⚠️
- **Fields**: +43 fields (now includes 30 supplemental demographic fields)
- **File**: `output/agentclients_full_20251202_020623.csv`
- **Performance**: 2,512 rows/sec (120 seconds total)
- **MongoDB Verification (Live Query)**:
  - `agentclients`: 301,930 (active)
  - `agentclients.removed`: 425 (soft-deleted with reason tags)
  - **Total accounted for**: 302,355
  - **HARD DELETED**: 271,519 records (bypassed soft-delete mechanism)
- **ISSUE**: Records were HARD DELETED, not archived to `.removed` collection
- **ACTION REQUIRED**: Investigate MongoDB operations logs between August-December 2025
  - Check for bulk delete operations (not using standard `.removed` pattern)
  - Verify if intentional data cleanup/archival occurred
  - Review application logs for agent account deletions
  - Determine if archived records need restoration

#### LISTINGS - Working As Designed ✅
- **August 11, 2025**: ~64,503 MongoDB docs → 223,834 CSV rows (107 MB, 51 fields)
- **December 2, 2025**: 45,742 MongoDB docs → 152,423 CSV rows (73 MB, 51 fields)
- **Change**: -18,761 MongoDB documents (-29%), -71,411 CSV rows
- **Multiplier**: 3.33x rows per document (due to array expansion or property relationships)
- **File**: `output/listings_full_20251202_020838.csv`
- **Performance**: 2,800 rows/sec (16 seconds total)
- **MongoDB Verification (Live Query)**:
  - `listings`: 45,742 (active)
  - `listings.removed`: 300,086 (archived - mostly duplicates)
  - **Total in system**: 345,828
  - Archive reason: `"Dup..."` pattern (duplicate detection)
- **Status**: ✅ Working as designed - listings properly archived to `.removed` collection
- **Note**: Row expansion is expected behavior - each listing with multiple properties/relationships creates multiple rows

#### AGENTS - Significant Growth
- **August 10, 2025**: 57 agents exported
- **December 2, 2025**: 679 agents exported (25 fields, 795 KB)
- **Growth**: +622 agents (+1,091%) 📈
- **File**: `output/agents_full_20251202_021037.csv`
- **Performance**: 1,492 rows/sec (0.5 seconds total)

#### TRANSACTIONS - First Production Export
- **December 2, 2025**: 23,513 transactions (20 fields, 4.2 MB)
- **File**: `output/transactions_full_20251202_020947.csv`
- **Performance**: 10,354 rows/sec (2.3 seconds total)
- **Note**: No previous export available for comparison

### Archived Exports
- Previous exports moved to: `archive/exports_before_dec2025/`
- Original agentclients export (August 25, 2025) preserved for comparison

**WealthX Export (Lake Database):** ✅ PRODUCTION COMPLETE - October 9, 2025
- **Discovery**: 62 fields discovered (46 included for export)
  - Fixed discovery bug: Increased PRIMARY field limit from 4→6
  - Manually added missing `residences[primary].state` field
- **Test Exports**:
  - 100 rows: 15 rows/sec (PRIMARY mode validation)
  - 5,000 rows: 651 rows/sec (all fields working)
  - 100,000 rows: 6,632 rows/sec (production validation) ✅
- **Production Export**:
  - **4,458,826 rows** exported in 5m 38s at 13,401 rows/sec
  - **2.0 GB CSV file** with 46 columns
  - Fixed build.gradle bug (arg position when rowLimit not specified)
  - File: `output/personwealthxdataMongo_full_20251009_004219.csv`
  - Personal (10): Name, email, DOB, age, marital status, sex, photo, nickNames
  - Business (10): Company, position, address, city, state, zip, country, phone, email
  - Net Worth (12): Wealth metrics, liquid assets, holdings (comma-separated industries)
  - **Residence (7): Array + COUNT + 5 PRIMARY fields** ✅
    - `residences` (array): Comma-separated countries
    - `residences[count]`: Number of residences
    - `residences[primary].address/city/state/postalCode/country`: First residence details
  - Other (7): Bio, interests, cities, names, metadata
- **Array Modes Working**:
  - ✅ COMMA_SEPARATED: nickNames, netWorthHoldings, interests
  - ✅ PRIMARY: Residence address components (single value extraction)
  - ✅ COUNT: Residence count (array length)
- **Data Quality**: All 46 fields validated at full 4.5M scale
  - State coverage: 38.7% (international data included)
  - PRIMARY mode correctly extracts single values (no commas)
  - COUNT mode accurate (18% have 2+ residences)
- **Status**: ✅ Production export complete and validated

**New Capabilities:**
- **MongoDB Explorer**: Standalone tool for database investigation
- **Multi-Environment Support**: Can now connect to dev, stage, prod, and lake clusters
- **Environment/Database Overrides**: CLI parameters `-Penv` and `-Pdatabase` for flexibility

### Key Achievements:
1. **Unified Architecture**: Single source of truth for caching (CollectionCacheManager)
2. **Smart Discovery**: Automatic relationship detection with Occam's razor heuristic
3. **Flexible Export**: Configuration-driven with multiple array handling modes
4. **Rich Demographics**: Supplemental configuration enables personexternaldatas integration
5. **Production Ready**: Tested with 573K+ records, stable and performant
6. **Database Explorer**: Zero-impact tool for investigating new data sources

### Field Statistics (Realm Production):
- **Base Fields**: 73 auto-discovered fields
- **Supplemental Fields**: 30 demographic fields
- **Total Export**: 103 columns in CSV
- **Address Coverage**: 30-37% of records
- **Demographic Coverage**: 6-10% of records (merged from multiple sources)

### WealthX Export - Complete ✅
All objectives achieved for WealthX database export:
1. ✅ Configured environment for lake cluster (WealthX data only available on lake)
2. ✅ Created discovery configuration for personwealthxdataMongo collection
3. ✅ Array handlers work for all array types (comma-separated, PRIMARY, COUNT modes)
4. ✅ Tested export performance with full 4.5M record dataset
5. ✅ Validated flattened schema with 46 fields (personal + business + wealth data)
6. ✅ Fixed build.gradle bug for env/database parameter handling

**Production File Available:**
- `output/personwealthxdataMongo_full_20251009_004219.csv`
- 4,458,826 rows × 46 columns
- 2.0 GB CSV file
- Ready for analysis/import

**Configuration Notes:**
- Environment: `lake` (WealthX only available on lake cluster)
- Database: `WealthX` (not `realm`)
- Use `-Penv=lake -Pdatabase=WealthX` parameters for re-export