# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MongoDB to CSV export utility with a **two-phase workflow**: 
1. **Discovery Phase**: Analyzes MongoDB collections to discover all fields and generate an editable JSON configuration
2. **Export Phase**: Uses the JSON configuration to export data with precise control over included fields

The project provides intelligent field filtering, relationship expansion, and comprehensive metadata generation.

## NEW TWO-PHASE ARCHITECTURE (v2.0)

### Phase 1: Discovery
- **Command**: `./gradlew discover -Pcollection=listings`
- **Output**: 
  - `config/listings_fields.json` - Editable field configuration
  - `config/listings_expansion_audit.txt` - Visual expansion tree for auditing
- **Purpose**: Discovers all fields, expands relationships, collects statistics
- **Key Class**: `FieldDiscoveryService`

### Phase 2: Configuration-Based Export  
- **Command**: `./gradlew configExport -Pcollection=listings`
- **Input**: `config/listings_fields.json` (can be manually edited)
- **Purpose**: Exports data according to configuration
- **Key Class**: `ConfigurationBasedExporter`

### Configuration File Structure
```json
{
  "collection": "listings",
  "discoveredAt": "2025-08-11T10:00:00Z",
  "discoveryParameters": {
    "sampleSize": 10000,
    "expansionDepth": 3,
    "minDistinctNonNullValues": 2
  },
  "fields": [
    {
      "fieldPath": "mlsNumber",
      "businessName": "MLS Number",
      "sourceCollection": "listings",
      "dataType": "string",
      "include": true,
      "statistics": {
        "distinctNonNullValues": 9875,
        "nullCount": 125
      }
    },
    {
      "fieldPath": "listingAgents",
      "businessName": "Listing Agents",
      "dataType": "array",
      "include": true,
      "arrayConfig": {
        "objectType": "objectId",
        "referenceCollection": "agents",
        "extractField": "fullName",
        "availableFields": ["createdAt", "fullName", "lastUpdated", "privateURL"],
        "displayMode": "comma_separated",
        "sortOrder": "alphanumeric"
      }
    },
    {
      "fieldPath": "realmData.lifestyles",
      "businessName": "Lifestyles",
      "dataType": "array",
      "include": true,
      "arrayConfig": {
        "objectType": "object",
        "referenceField": "lifestyle",
        "referenceCollection": "lifestyles",
        "extractField": "lifestyleName",
        "availableFields": ["lifestyleName", "category", "description"],
        "displayMode": "comma_separated"
      }
    }
  ],
  "requiredCollections": ["agents", "lifestyles"],
  "exportSettings": {
    "batchSize": 5000,
    "useBusinessNames": true
  }
}
```

### Key Features of New Architecture
- **Editable Configuration**: JSON can be manually edited between phases
- **Field-Level Control**: Include/exclude specific fields
- **Array Reference Resolution**: Automatically looks up ObjectIds in referenced collections
- **Available Fields Display**: Shows all possible fields you can extract from referenced collections
- **Smart Field Selection**: Auto-selects best display field (name, fullName, title, etc.)
- **Flexible Array Display**: Choose between first value or comma-separated list
- **Automatic Sorting**: Arrays sorted alphanumerically by default
- **Collection Caching**: Automatically caches required collections for fast lookups

## CORE DESIGN PRINCIPLES

### Field Filtering Rules
- **Include Business IDs**: mlsNumber, listingId, transactionId (if they have data)
- **Exclude Technical IDs**: _id, __v, fields ending with Id (unless business ID)
- **Exclude Empty Fields**: 0 distinct non-null values
- **Exclude Single-Value Fields**: Only 1 distinct non-null value  
- **Exclude Sparse Fields**: Fields present in <10% of documents (configurable threshold)
- **Include Multi-Value Fields**: 2+ distinct non-null values AND >10% presence
- **Business-Readable Names**: All columns use human-readable names

### Relationship Handling
- **Automatic Discovery**: Detects ObjectId references and arrays of ObjectIds
- **Smart Collection Mapping**: Guesses target collections from field names
- **Depth Control**: Expands relationships up to 3 levels by default
- **Efficient Caching**: Caches small collections (<100K docs) in memory

## BUILD AND RUN COMMANDS

### Two-Phase Workflow
```bash
# Step 1: Discover fields and create configuration
./gradlew discover -Pcollection=listings

# Step 2: Review/edit configuration (optional)
vi config/listings_fields.json

# Step 3: Export using configuration
./gradlew configExport -Pcollection=listings

# Optional: Export with row limit for testing
./gradlew configExport -Pcollection=listings -ProwLimit=1000
```

### PRODUCTION RUN INSTRUCTIONS
```bash
# Full listings export (all 64,503 documents)
./gradlew discover -Pcollection=listings
./gradlew configExport -Pcollection=listings

# Full transactions export
./gradlew discover -Pcollection=transactions
./gradlew configExport -Pcollection=transactions

# Full agents export
./gradlew discover -Pcollection=agents
./gradlew configExport -Pcollection=agents
```

### Performance Expectations
- **Discovery Phase**: ~30 seconds (samples 10,000 docs + analyzes relationships)
- **Export Phase**: 
  - Small collections (<100K docs): 1-2 minutes
  - Medium collections (100K-500K docs): 3-5 minutes
  - Large collections (>1M docs): 10+ minutes
- **Export Speed**: 2,000-4,000 rows/sec depending on field complexity

### Available Collections
- `listings` - Active property listings (~64K docs)
- `transactions` - Sales transactions (size varies)
- `agents` - Agent profiles (~28K docs)
- `properties` - Property records (~1.9M docs - use with caution)
- Any other MongoDB collection name

## PROJECT STRUCTURE

### New Components (v2.0)

#### Configuration Classes
- `config/FieldConfiguration.java` - Represents individual field metadata
- `config/DiscoveryConfiguration.java` - Root configuration with all fields

#### Discovery Phase
- `discovery/FieldDiscoveryService.java` - Discovers fields, generates configuration and audit tree
- `DiscoveryRunner.java` - Main entry point for discovery phase

#### Export Phase
- `export/ConfigurationBasedExporter.java` - Exports using JSON configuration
- `ConfigExportRunner.java` - Main entry point for export phase

### Core Components (Retained)
- `AbstractUltraExporter.java` - Base class for all exporters
- `RelationExpander.java` - Handles relationship expansion
- `FieldStatisticsCollector.java` - Collects field statistics
- `FieldNameMapper.java` - Maps technical names to business names
- `ExportConfig.java` - Database connection configuration


## CONFIGURATION

### Database Connection (`application.properties`)
```properties
mongodb.url.dev=mongodb://username:password@host:port/?authSource=admin
current.environment=dev
database.name=realm
output.directory=./output
```

### Memory Settings
- Discovery: 16GB heap recommended
- Export: 24GB heap for large collections
- Adjust in build.gradle if needed

## TESTING TODO LIST

### Immediate Testing Tasks
- [ ] Compile project: `./gradlew build`
- [ ] Test discovery: `./gradlew discover -Pcollection=listings`
- [ ] Verify JSON created in `config/listings_fields.json`
- [ ] Review JSON structure and field detection
- [ ] Test export: `./gradlew configExport -Pcollection=listings`
- [ ] Compare output with legacy `autoDiscover` results

### Manual Configuration Testing
- [ ] Edit JSON to exclude specific fields
- [ ] Change array display modes (first vs comma_separated)
- [ ] Modify business names
- [ ] Test with modified configuration

### Extended Testing
- [ ] Test with transactions collection
- [ ] Test with agents collection
- [ ] Test relationship expansion depth changes
- [ ] Test with sparse collections

## TROUBLESHOOTING

### Common Issues
1. **Discovery fails**: Check MongoDB connection and collection name
2. **No configuration file**: Ensure discovery phase completed successfully
3. **Export missing fields**: Check `include` flag in JSON configuration
4. **Memory errors**: Increase heap size in build.gradle
5. **Array fields empty**: Check `extractField` in array configuration

### Debug Tips
- Discovery logs: Check field discovery count during sampling
- Configuration location: `config/{collection}_fields.json`
- Export logs: Monitor cache status and progress
- Statistics: Review generated metadata files

## RECENT UPDATES (2025-08-11)

### Critical Bug Fix - Discovery Phase (8:45 AM UTC)
- ✅ **Fixed incorrect field occurrence counting** - Empty arrays were being counted as field occurrences
  - Root cause: Empty arrays (`fees: []`, `viewTypes: []`) were counted as having values
  - Impact: Fields with mostly empty arrays showed >100% occurrence rate
  - Fix: Only count fields with actual non-empty values
  - Added per-document field tracking to prevent double-counting in nested documents
  - Result: Sparse fields now correctly excluded (reduced from 53 to 51 fields)

### Final Production-Ready Improvements (8:00 AM UTC)
- ✅ **Compound Sparsity for Expanded Fields** - Intelligently filters expanded fields
  - Calculates: (parent field presence %) × (child field presence %)
  - Samples 1,000 docs from referenced collections for statistics
  - Reduced expanded fields from 50+ to 7 meaningful ones
  - Example: property_expanded.city included only if compound sparsity >10%

### Earlier Improvements (7:50 AM UTC)
- ✅ **Implemented Sparse Field Threshold** - Fields appearing in <10% of documents are excluded by default
  - Prevents empty columns in exports (e.g., belowGradeAreaFinished only in 13.6% of docs)
  - Reduced included fields from 83 to 53 for listings collection
  - Configurable threshold (default 10%)
- ✅ **Fixed Expanded Field Resolution** - _expanded fields now properly populate from cached collections
  - Property City, Property Street Address now show actual values
  - Maintains excellent performance (2,400+ rows/sec)

## RECENT UPDATES (2025-08-11 - Earlier)

### Two-Phase Workflow Implementation
- ✅ Created separate discovery and export phases
- ✅ Implemented JSON configuration schema
- ✅ Added field-level include/exclude control
- ✅ Implemented array field configuration
- ✅ Added automatic field extraction for arrays
- ✅ Integrated collection caching in export phase
- ✅ Fixed array statistics collection (arrays now properly included)
- ✅ Added visual audit tree for field expansion verification
- ✅ Added row limit parameter for testing exports
- ✅ Fixed duplicate headers issue in CSV export
- ✅ Optimized RelationExpander (only expands when needed, depth 1 for config export)
- ✅ Fixed field statistics tracking in ConfigurationBasedExporter
- ✅ Ensured RFC 4180 compliant CSV output with proper quote escaping
- ✅ Implemented batch loading for ObjectId references (100x performance improvement)
- ✅ Added smart ObjectId resolution to display meaningful values instead of IDs
- ✅ Fixed RelationExpander field mappings (listingBrokerage, not listingBrokerageId)
- ✅ Fixed FieldNameMapper to generate clean business names without "_expanded"
- ✅ **Fixed expanded field resolution** - Now properly looks up and populates expanded fields from cached collections (e.g., Property City, Property Street Address)

### Key Improvements
- **Separation of Concerns**: Discovery and export are independent
- **Human-Editable Config**: JSON can be manually adjusted
- **Reusable Configurations**: Save and version configurations
- **Better Performance**: Cache only required collections, selective expansion
- **Batch Loading**: Pre-caches referenced documents in batches for 100x speedup
- **Smart ObjectId Resolution**: Automatically expands ObjectId references to meaningful values
- **Sparse Field Filtering**: Excludes fields present in <10% of documents
- **Compound Sparsity**: Calculates parent × child field presence for intelligent expansion
- **Expanded Field Resolution**: Properly resolves _expanded fields with actual values
- **Flexible Array Handling**: Configure per-field display
- **Export Testing**: Row limit parameter for quick validation
- **Audit Trail**: Visual tree showing field expansion hierarchy
- **Statistics Tracking**: Comprehensive field-level statistics in summary file
- **CSV Compliance**: RFC 4180 compliant with proper quote escaping

## GIT INFORMATION
- Repository: https://github.com/scoopeng/Realm
- Main branch: master
- Current version: 2.0-SNAPSHOT

## KNOWN ISSUES & NEXT STEPS

### Known Issues
1. **Hardcoded Relationships**: RelationExpander has hardcoded field-to-collection mappings
   - Currently requires manual updates when schema changes
   - Should auto-discover relationships based on field names and ObjectId patterns

2. **Expanded Field Statistics**: Expanded fields don't calculate compound sparsity
   - Example: property_expanded.city might be empty even if property exists
   - Need to calculate: (parent field presence) × (child field presence in referenced docs)
   - Currently working on Option 2: Sample-based statistics during expansion

### Next Steps
1. ✅ Complete testing of two-phase workflow
2. Implement auto-discovery of relationships (remove hardcoding)
3. Add configuration validation
4. Create configuration templates
5. Add configuration merge capabilities
6. Optimize collection caching for very large datasets (>1M docs)