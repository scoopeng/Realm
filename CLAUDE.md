# CLAUDE.md - MongoDB Export Utility
*Last Updated: October 8, 2025 - WealthX Database Discovery*

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

### Available Collections
- `listings` - ~64K property listings
- `transactions` - Sales transactions
- `agents` - ~28K agent profiles
- `agentclients` - ~573K agent-client relationships
- `properties` - ~1.9M properties (use with caution)

### Performance Expectations
- **Discovery**: ~2-3 minutes for 10K sample with expansion
- **Export**: 
  - <100K docs: 1-2 minutes
  - 100K-500K docs: 3-5 minutes  
  - >1M docs: 10+ minutes
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
- **dev**: Development database with `realm` database
- **stage**: Staging database with `realm` database
- **prod**: Production database with `realm` database (current default)
- **lake**: Big data cluster with `realmLake` and `WealthX` databases

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

### Available Databases on Lake Cluster
1. **WealthX** - Primary wealth data (5M+ records)
2. **realmLake** - ListHub real estate data (24M+ records)

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

## CURRENT STATUS (October 8, 2025)

### ✅ System Fully Operational

**Production Exports (Realm Database):**
- **Discovery**: Finds 73 fields with intelligent filtering
- **Export**: Successfully exports 573,874 rows with 103 total fields
- **Supplemental System**: Adds 30 demographic fields via reverse lookup
- **Performance**: ~600 rows/sec export speed
- **Data Quality**: All major field groups working with expected coverage

**New Capabilities:**
- **MongoDB Explorer**: Standalone tool for database investigation (October 8, 2025)
- **WealthX Database Discovered**: 4.5M wealth records on lake cluster
- **Multi-Environment Support**: Can now explore dev, stage, prod, and lake clusters

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

### Next Steps: WealthX Export Development
1. **Create discovery configuration** for personwealthxdataMongo collection
2. **Enhance array handlers** for complex nested arrays (netWorthHoldings)
3. **Implement aggregate functions** for asset statistics
4. **Test export performance** with 4.5M record dataset
5. **Design flattened schema** with 50-80 fields (personal + business + wealth data)