# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MongoDB to CSV export utility designed for flexible data extraction with special handling for multi-value fields. The project exports MongoDB collections to CSV format with intelligent field filtering and comprehensive metadata generation.

### Core Requirements and Design Decisions
- **NO ID FIELDS IN OUTPUT**: All fields ending with "Id", "_id", or containing "._id" or ".@reference" are excluded from exports
- **Business-Readable Names**: All column headers use human-readable business names instead of technical field paths
- **Automatic Foreign Key Expansion**: All ObjectId references are automatically expanded to include related document data
- **Consistent Behavior**: All three collections (listings, transactions, agents) must use identical processing logic
- **Expansion Depth**: Relations are expanded up to 3 levels deep to capture nested relationships
- **Binary Fields Included**: Fields with 2 distinct non-null values (e.g., true/false) are included as they contain meaningful information
- **Single-Value Fields Excluded**: Fields with only 1 distinct non-null value are excluded as they provide no analytical value
- **Sample Size**: Discovery phase samples 5000 documents to determine field characteristics

### Current State (as of 2025-08-09)
- ✅ Core functionality implemented and tested
- ✅ Three comprehensive exporters (Listings, Transactions, Agents)
- ✅ Smart field filtering and scanning capabilities
- ✅ Machine-readable metadata generation
- ✅ Refactored with common base classes
- ✅ Auto-discovery with automatic field detection
- ✅ Filtered export using discovery results
- ✅ Fixed CSV escaping (RFC 4180 compliant)
- ✅ Performance optimizations (disabled expansion for filtered mode)
- ✅ Initial commit pushed to GitHub: https://github.com/scoopeng/Realm
- ⚠️ MongoDB connection requires valid credentials in application.properties

## Build and Run Commands

### Quick Start - The 2-Step Process
```bash
# Step 1: Discover everything automatically
./gradlew autoDiscover -Pcollection=listings

# Step 2: Get clean data with only valid columns  
./gradlew filteredExport -Pcollection=listings
```

### Primary Commands

#### Auto Discovery (RECOMMENDED - Primary Export Method)
`./gradlew autoDiscover -Pcollection=listings`
`./gradlew autoDiscover -Pcollection=transactions`
`./gradlew autoDiscover -Pcollection=agents`

**What it does:**
- Automatically discovers ALL fields in the collection by sampling 5000 documents
- Expands all foreign key relationships up to 3 levels deep
- Excludes all ID fields from the output (no _id, no fields ending with Id)
- Uses business-readable column names for all fields
- Filters out single-value fields but keeps binary fields (true/false)
- Generates comprehensive field statistics and metadata
- Creates consistent exports across all three collections using identical logic

#### Filtered Export
`./gradlew filteredExport`
- Uses saved summaries from autoDiscover
- Excludes empty, single-value, and sparse columns
- Gives you clean data with only meaningful fields
- Much smaller file size

### Standard Exports (Original)
- **Listings**: `./gradlew exportListings` (~192 columns, 3.5K/sec)
- **Transactions**: `./gradlew exportTransactions` (~120 columns, 5.6K/sec)
- **Agents**: `./gradlew exportAgents` (~150 columns, 20K/sec)

### Smart Export (Legacy)
- **Smart Export**: `./gradlew smartExport -PexportArgs="export listings --scan-first --exclude-empty"`

## Project Architecture

### Core Components

1. **AbstractUltraExporter** (`src/main/java/com/example/mongoexport/AbstractUltraExporter.java`)
   - Base class for all exporters
   - Common CSV writing and statistics logic
   - Progress logging and error handling
   - Supports relation expansion and field statistics

2. **RelationExpander** (`src/main/java/com/example/mongoexport/RelationExpander.java`) **(NEW)**
   - Automatic expansion of related collections
   - Configurable expansion depth (default: 2 levels)
   - Intelligent caching for frequently accessed collections
   - Handles circular references and complex relationships

3. **FieldStatisticsCollector** (`src/main/java/com/example/mongoexport/FieldStatisticsCollector.java`) **(NEW)**
   - Real-time field statistics collection during export
   - Categorizes fields: empty, single-value, sparse, meaningful
   - Generates machine-readable summaries
   - Supports filtering based on saved summaries

4. **ComprehensiveExporter** (`src/main/java/com/example/mongoexport/ComprehensiveExporter.java`) **(NEW)**
   - Main entry point for comprehensive exports
   - Supports multiple modes: full, analyze, filtered, minimal
   - Handles all three object types (listings, transactions, agents)
   - Command-line argument parsing

5. **ExportConfig** (`src/main/java/com/example/mongoexport/ExportConfig.java`)
   - Loads configuration from `application.properties`
   - Manages environment selection (dev/stage/prod/lake)
   - Creates output directory automatically

6. **ExportOptions** (`src/main/java/com/example/mongoexport/ExportOptions.java`)
   - Enhanced configurable export behavior
   - Relation expansion settings
   - Field statistics collection
   - Summary-based filtering

7. **FieldScanner** (`src/main/java/com/example/mongoexport/FieldScanner.java`)
   - Analyzes MongoDB collections for field usage
   - Supports nested documents and arrays
   - Generates field usage reports

8. **SmartExporter** (`src/main/java/com/example/mongoexport/SmartExporter.java`)
   - Legacy command-line interface for smart exports
   - Field filtering based on usage analysis

### Specific Exporters

#### Original Exporters
1. **UltraListingsExporter**
   - Comprehensive property listings export (~192 columns)
   - In-memory collection loading (20GB heap)
   - Property details, agent info, brokerage data
   - Performance: ~3,500 listings/second

2. **UltraTransactionExporter**
   - Complete transaction history (~120 columns)
   - Resolves foreign keys to readable names
   - Buyer/seller details, financial data
   - Performance: ~5,600 transactions/second

3. **UltraAgentPerformanceExporter**
   - Agent performance metrics (~150 columns)
   - Professional credentials, sales metrics
   - Team affiliations and awards
   - Performance: ~20,000 agents/second

#### Enhanced Exporters (NEW)
1. **EnhancedListingsExporter**
   - Extends AbstractUltraExporter
   - Automatic relation expansion to properties, agents, brokerages
   - Field statistics collection
   - Summary-based filtering support

2. **EnhancedTransactionExporter**
   - Extends AbstractUltraExporter
   - Stub implementation (delegates to original for now)
   - Ready for relation expansion implementation

3. **EnhancedAgentExporter**
   - Extends AbstractUltraExporter
   - Stub implementation (delegates to original for now)
   - Ready for relation expansion implementation

## Smart Export Features

### Field Scanning
The field scanner analyzes collections to identify:
- **Empty fields**: Always null/empty (can be excluded)
- **Single-value fields**: Only one distinct value (can be excluded)
- **Sparse fields**: >95% null (threshold configurable)
- **Meaningful fields**: Contain useful, varied data

### Export Options
```bash
--exclude-empty          # Skip always-empty columns
--exclude-single-value   # Skip single-value columns  
--exclude-sparse         # Skip sparse columns (>95% null)
--sparse-threshold 90    # Custom sparse threshold
--scan-first            # Analyze fields before export
--scan-sample 5000      # Custom scan sample size
```

### Output Files
Each export generates three files:
1. **CSV**: `{prefix}_ultra_comprehensive_{timestamp}.csv`
2. **Statistics**: `{prefix}_ultra_comprehensive_{timestamp}_stats.txt`
3. **Metadata**: `{prefix}_ultra_comprehensive_{timestamp}_metadata.json`

## Configuration

### application.properties
```properties
mongodb.url.dev=mongodb://username:password@host:port/?authSource=admin
mongodb.url.stage=...
mongodb.url.prod=...
current.environment=dev
database.name=realm
output.directory=./output
```

### Memory Settings
- Default: 20GB heap for large collections
- Adjust in build.gradle if needed
- Batch processing minimizes memory usage

## Database Structure

### Key Collections
1. **properties** (1.9M docs) - Property data with addresses
2. **listings** (64K active) - Active listings with pricing
3. **agents** (28K docs) - Agent profiles and credentials
4. **transactions** (23K docs) - Sales history and parties
5. **people** (620K docs) - Person records for agents/clients
6. **agentclients** (572K docs) - Agent-client relationships

### Relationships
- listings → properties (via property field)
- listings → agents (via listingAgentId)
- transactions → listings and properties
- agents → people (via person field)

## New Features (2025-08-06)

### Automatic Relation Expansion
The system now automatically expands related collections during export:
- **Properties**: Full property details including address, features, and history
- **Agents**: Agent profiles with personal information and credentials
- **Brokerages**: Brokerage details and contact information
- **Transactions**: Related transactions with buyer/seller information
- **People**: Personal details for agents and clients

Configurable expansion depth (default: 2 levels) prevents infinite recursion.

### Field Statistics Collection
Real-time statistics collection during export:
- Tracks null/empty values per field
- Counts unique values
- Categorizes fields as empty, single-value, sparse, or meaningful
- Generates JSON summaries for future filtering

### Export Modes
1. **Full Mode**: Complete export with expansion and statistics
2. **Analyze Mode**: Statistics collection without expansion
3. **Filtered Mode**: Uses saved summaries to exclude sparse fields
4. **Minimal Mode**: Basic export without extras

## Common Tasks

### Typical Workflow
```bash
# 1. First run: Analyze and collect statistics
./gradlew analyzeFields

# 2. Review generated summaries in output/
ls output/*_summary.json

# 3. Run filtered export using summaries
./gradlew filteredExport

# 4. Or run full export with everything
./gradlew comprehensiveExport
```

### Custom Export Options
```bash
# Export specific collection with custom mode
./gradlew comprehensiveExport -Pmode=full -Pcollection=listings

# Export with custom expansion depth
java -cp build/libs/Realm-1.0-SNAPSHOT.jar \
  com.example.mongoexport.ComprehensiveExporter \
  full listings --depth=3
```

### Analyze Field Usage
```bash
# Scan a collection to see field statistics
./gradlew scanListings

# View results in output/listings_field_scan.json
```

### Run Smart Export
```bash
# Export with automatic field filtering
./gradlew smartExportListings

# This will:
# 1. Scan the collection (10K sample)
# 2. Identify empty/sparse fields
# 3. Export only meaningful columns
# 4. Generate metadata and statistics
```

### Custom Smart Export
```bash
java -cp build/libs/Realm-1.0-SNAPSHOT.jar \
  com.example.mongoexport.SmartExporter \
  export listings \
  --scan-first \
  --exclude-empty \
  --exclude-sparse \
  --sparse-threshold 80
```

### Analyze Previous Export
```bash
java -cp build/libs/Realm-1.0-SNAPSHOT.jar \
  com.example.mongoexport.SmartExporter \
  analyze-metadata output/listings_metadata.json
```

## Technical Stack
- Java 11
- MongoDB Driver 5.2.1
- OpenCSV 5.9
- Jackson 2.15.3 (JSON)
- Logback 1.4.14
- Typesafe Config 1.4.3
- Gradle 8.13

## Troubleshooting

### If exports are timing out or running slowly:
1. Check memory settings in build.gradle (default: 20GB heap)
2. Ensure MongoDB connection is stable
3. Consider reducing discovery sample size if needed
4. Clean output directory: `rm -f ./output/*`

### Key implementation details:
- All exports use `AutoDiscoveryExporter` as the single source of truth
- Field filtering happens in `filterFieldsByDistinctValues()` method
- ID fields are explicitly excluded during the filtering phase
- Business names are mapped via `FieldNameMapper.getBusinessName()`
- Foreign key expansion is handled by `expandDocumentRelations()`

## Git Information
- Repository: https://github.com/scoopeng/Realm
- Main branch: master
- Current version: 2.0-SNAPSHOT

## Recent Updates (2025-08-09)
- ✅ Made AutoDiscoveryExporter the single source of truth for all exports
- ✅ Implemented exclusion of all ID fields from output
- ✅ Fixed binary field inclusion (2 distinct non-null values are meaningful)
- ✅ Increased discovery sample size to 5000 documents
- ✅ Restored expansion depth to 3 levels
- ✅ Ensured consistent behavior across all three collections
- ✅ Improved field filtering logic to handle expanded fields better

## Previous Updates (2025-08-06)
- ✅ Added base class abstraction for exporters
- ✅ Implemented automatic relation expansion (RelationExpander)
- ✅ Added real-time field statistics collection (FieldStatisticsCollector)
- ✅ Created comprehensive export system with multiple modes
- ✅ Enhanced exporters with expansion and filtering capabilities
- ✅ Fixed CSV quote escaping to use RFC 4180 standard (quote doubling)
- ✅ Fixed row count tracking for accurate statistics
- ✅ Optimized filtered export performance (disabled expansion, fixed summary usage)
- ✅ Fixed gradle tasks to respect collection parameter
- ✅ Cleaned up excessive debug logging
- ✅ Updated all documentation with latest features