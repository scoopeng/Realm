# CLAUDE.md - MongoDB Export Utility
*Last Updated: August 25, 2025 - Post-Cleanup*

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

Configure in `application.properties`:
```properties
mongodb.url.dev=mongodb://username:password@host:port/?authSource=admin
current.environment=dev
database.name=realm
output.directory=./output
```

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

## CURRENT STATUS (August 25, 2025)

### ✅ System Fully Operational
- **Discovery**: Finds 73 fields with intelligent filtering
- **Export**: Successfully exports 573,874 rows with 103 total fields
- **Supplemental System**: Adds 30 demographic fields via reverse lookup
- **Performance**: ~600 rows/sec export speed
- **Data Quality**: All major field groups working with expected coverage

### Key Achievements:
1. **Unified Architecture**: Single source of truth for caching (CollectionCacheManager)
2. **Smart Discovery**: Automatic relationship detection with Occam's razor heuristic
3. **Flexible Export**: Configuration-driven with multiple array handling modes
4. **Rich Demographics**: Supplemental configuration enables personexternaldatas integration
5. **Production Ready**: Tested with 573K+ records, stable and performant

### Field Statistics:
- **Base Fields**: 73 auto-discovered fields
- **Supplemental Fields**: 30 demographic fields
- **Total Export**: 103 columns in CSV
- **Address Coverage**: 30-37% of records
- **Demographic Coverage**: 6-10% of records (merged from multiple sources)