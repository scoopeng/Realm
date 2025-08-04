# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MongoDB to CSV export utility designed for flexible data extraction with special handling for multi-value fields. The project was created to export MongoDB collections to CSV format with two distinct strategies for handling array/list fields.

### Current State (as of 2025-08-01)
- ✅ Core functionality implemented and tested
- ✅ Both export strategies working correctly
- ✅ Sorting implemented for DELIMITED strategy
- ✅ Initial commit pushed to GitHub: https://github.com/scoopeng/Realm
- ⚠️ MongoDB connection requires valid credentials in application.properties
- 📝 Test mode available via `./gradlew runTest`

## Build and Run Commands

- **Build**: `./gradlew build`
- **Ultra Listings Export**: `./gradlew runUltraListings` (~192 columns, 3.5K/sec)
- **Ultra Transaction Export**: `./gradlew runUltraTransaction` (~120 columns, 5.6K/sec)
- **Ultra Agent Performance Export**: `./gradlew runUltraAgentPerformance` (~150 columns, 20K/sec)
- **Clean**: `./gradlew clean`
- **Test**: `./gradlew test`

## Project Architecture

### Core Components

1. **ExportConfig** (`src/main/java/com/example/mongoexport/ExportConfig.java`)
   - Loads configuration from `application.properties`
   - Manages environment selection (dev/stage/prod)
   - Creates output directory automatically
   - Validates MongoDB connection strings

2. **UltraListingsExporter** (`src/main/java/com/example/mongoexport/UltraListingsExporter.java`)
   - Comprehensive property listings export (~192 human-readable columns)
   - In-memory collection loading for performance (20GB heap)
   - Includes property details, agent info, brokerage data, market profiles
   - Performance: ~3,500 listings/second
   - Removes ~67 meaningless columns that were always false/null

3. **UltraTransactionExporter** (`src/main/java/com/example/mongoexport/UltraTransactionExporter.java`)
   - Complete transaction history export (~120 human-readable columns)
   - Resolves all foreign keys to human-readable names
   - Includes buyer/seller details, agent info, property characteristics
   - Performance: ~5,600 transactions/second
   - Optimized batch processing

4. **UltraAgentPerformanceExporter** (`src/main/java/com/example/mongoexport/UltraAgentPerformanceExporter.java`)
   - Comprehensive agent performance metrics (~150 human-readable columns)
   - Includes professional credentials, performance metrics, team affiliations
   - Performance: ~20,000 agents/second
   - Memory-optimized with in-memory lookups

9. **EnhancedPreJoinExporter** (`src/main/java/com/example/mongoexport/EnhancedPreJoinExporter.java`)
   - Advanced version with denormalized indicators for multi-value fields
   - **Property-Centric View**: 150+ columns including:
     - All property attributes and calculated metrics
     - Boolean indicators for each lifestyle (lifestyle_luxury, lifestyle_urban, etc.)
     - Boolean indicators for each tag and feature
     - School ratings and distances
     - Full agent and brokerage details
     - Market metrics and financial data
   - **Agent Performance View**: 100+ columns including:
     - Complete performance metrics (sales volume, transaction counts)
     - Specialty indicators as boolean columns
     - Price range specialization counts
     - Geographic coverage analysis
     - Marketing effectiveness metrics
   - Designed for wide-table analytics with easy filtering/grouping

## Export Strategy Details

All three exporters use a **comprehensive single-row strategy** where:
- Each row represents one primary entity (listing, transaction, or agent)
- Related data is joined and flattened into additional columns
- Foreign keys are resolved to human-readable names
- Boolean indicators show presence of tags, specializations, etc.
- Multi-value fields are represented as comma-separated values
- All column headers are human-readable for end users

## Configuration Details

### application.properties Structure
```properties
mongodb.url.dev=mongodb://username:password@host:port/?authSource=admin
mongodb.url.stage=...
mongodb.url.prod=...
current.environment=dev
database.name=realm
output.directory=./output
```

### Important Configuration Notes
- MongoDB URLs contain actual credentials (check application.properties)
- Output files are timestamped: `{exporter}_ultra_comprehensive_{yyyyMMdd_HHmmss}.csv`
- CSV files use UTF-8 encoding with proper quote handling
- All column headers are human-readable for end users

## Known Issues and Considerations

1. **MongoDB Connection**: Current credentials in application.properties may not be accessible from all environments
2. **Memory Usage**: Large collections are processed with cursors to minimize memory
3. **Null Handling**: Empty/null multi-value fields are handled gracefully
4. **Performance**: Tested with progress logging, suitable for large datasets

## Common Tasks

### Running Production Exports
```bash
# Run all three comprehensive exports
./gradlew runUltraListings
./gradlew runUltraTransaction  
./gradlew runUltraAgentPerformance
```

### Modifying Exports
To customize any exporter:
1. Open the appropriate Ultra*Exporter.java file
2. Modify the `buildComprehensiveHeaders()` method for column changes
3. Modify the `buildComprehensiveRow()` method for data changes
4. Ensure headers and data arrays match in length

### Configuration
- Edit `application.properties` or create `application-local.properties` (gitignored)
- Key settings: MongoDB URL, database name, output directory
- Memory: Increase JVM heap size in build.gradle if needed (currently 20GB)

## Database Structure Summary

### Key Collections for Real Estate Data
1. **properties** (1.9M docs) - Core property data with addresses and locations
2. **listings** (64K active) - Active property listings with features and pricing
3. **agents** (28K docs) - Real estate agent profiles
4. **transactions** (23K docs) - Completed sales with pricing and parties
5. **people** (620K docs) - Person records linked to agents
6. **agentclients** (572K docs) - Agent-client relationships

### Key Relationships
- listings → properties (via property field)
- listings → agents (via listingAgentId)
- transactions → listings and properties
- agents → people (via person field)

### Pre-Joined Export Views
1. **Complete Property Listings** - Comprehensive view with property details, agent info, and pricing
2. **Transaction History** - Sales data with buyer/seller info and financial details
3. **Agent Performance** - Metrics including sales volume, listing counts, and client numbers

## Technical Stack
- Java 11
- MongoDB Driver 5.2.1
- OpenCSV 5.9
- Logback 1.4.14
- Typesafe Config 1.4.3
- Gradle 8.13

## Git Information
- Repository: https://github.com/scoopeng/Realm
- Main branch: master
- Current version: 2.0-SNAPSHOT

## Output Files
All exports generate timestamped CSV files in the `output/` directory:
- `all_listings_ultra_comprehensive_YYYYMMDD_HHMMSS.csv`
- `transaction_history_ultra_comprehensive_YYYYMMDD_HHMMSS.csv`
- `agent_performance_ultra_comprehensive_YYYYMMDD_HHMMSS.csv`

## Documentation
Detailed documentation available for each exporter:
- `ULTRA_LISTINGS_DOCUMENTATION.md`
- `ULTRA_TRANSACTION_DOCUMENTATION.md`
- `ULTRA_AGENT_PERFORMANCE_DOCUMENTATION.md`
- `CLEANED_EXPORTER_CHANGES.md` (summary of improvements)