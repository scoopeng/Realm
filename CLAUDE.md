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
- **Output**: `config/listings_fields.json`
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
      "fieldPath": "openHouses[]",
      "businessName": "Open Houses",
      "dataType": "array",
      "include": true,
      "arrayConfig": {
        "extractField": "dateTime",
        "displayMode": "comma_separated",
        "sortOrder": "alphanumeric"
      }
    }
  ],
  "requiredCollections": ["properties", "agents"],
  "exportSettings": {
    "batchSize": 5000,
    "useBusinessNames": true
  }
}
```

### Key Features of New Architecture
- **Editable Configuration**: JSON can be manually edited between phases
- **Field-Level Control**: Include/exclude specific fields
- **Array Handling**: Configure how arrays are displayed (first value vs comma-separated)
- **Automatic Sorting**: Arrays sorted alphanumerically by default
- **Smart Field Selection**: Auto-selects first meaningful non-ID string field for array objects
- **Collection Caching**: Automatically caches required collections for relationships

## CORE DESIGN PRINCIPLES

### Field Filtering Rules
- **Include Business IDs**: mlsNumber, listingId, transactionId (if they have data)
- **Exclude Technical IDs**: _id, __v, fields ending with Id (unless business ID)
- **Exclude Empty Fields**: 0 distinct non-null values
- **Exclude Single-Value Fields**: Only 1 distinct non-null value  
- **Include Multi-Value Fields**: 2+ distinct non-null values
- **Business-Readable Names**: All columns use human-readable names

### Relationship Handling
- **Automatic Discovery**: Detects ObjectId references and arrays of ObjectIds
- **Smart Collection Mapping**: Guesses target collections from field names
- **Depth Control**: Expands relationships up to 3 levels by default
- **Efficient Caching**: Caches small collections (<100K docs) in memory

## BUILD AND RUN COMMANDS

### Primary Workflow (NEW)
```bash
# Step 1: Discover fields and create configuration
./gradlew discover -Pcollection=listings

# Step 2: Review/edit configuration (optional)
vi config/listings_fields.json

# Step 3: Export using configuration
./gradlew configExport -Pcollection=listings
```

### Legacy All-in-One (Still Available)
```bash
# Original auto-discovery export
./gradlew autoDiscover -Pcollection=listings
```

### Available Collections
- `listings` - Active property listings
- `transactions` - Sales transactions
- `agents` - Agent profiles
- Any other MongoDB collection name

## PROJECT STRUCTURE

### New Components (v2.0)

#### Configuration Classes
- `config/FieldConfiguration.java` - Represents individual field metadata
- `config/DiscoveryConfiguration.java` - Root configuration with all fields

#### Discovery Phase
- `discovery/FieldDiscoveryService.java` - Discovers fields and generates configuration
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

### Legacy Components
- `AutoDiscoveryExporter.java` - Original all-in-one exporter (still functional)

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

### Two-Phase Workflow Implementation
- ✅ Created separate discovery and export phases
- ✅ Implemented JSON configuration schema
- ✅ Added field-level include/exclude control
- ✅ Implemented array field configuration
- ✅ Added automatic field extraction for arrays
- ✅ Integrated collection caching in export phase
- ✅ Maintained backward compatibility with legacy exporter

### Key Improvements
- **Separation of Concerns**: Discovery and export are independent
- **Human-Editable Config**: JSON can be manually adjusted
- **Reusable Configurations**: Save and version configurations
- **Better Performance**: Cache only required collections
- **Flexible Array Handling**: Configure per-field display

## GIT INFORMATION
- Repository: https://github.com/scoopeng/Realm
- Main branch: master
- Current version: 2.0-SNAPSHOT

## NEXT STEPS
1. Complete testing of two-phase workflow
2. Optimize collection caching for large datasets
3. Add configuration validation
4. Create configuration templates
5. Add configuration merge capabilities