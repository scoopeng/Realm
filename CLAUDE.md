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
- **Run**: `./gradlew run` (requires MongoDB connection)
- **Test Export**: `./gradlew runTest` (runs without MongoDB)
- **Database Analysis**: `./gradlew runAnalyze` (analyzes database structure)
- **Pre-Join Export**: `./gradlew runPreJoin` (exports joined collections)
- **Enhanced Pre-Join**: `./gradlew runEnhancedPreJoin` (exports with denormalized indicators)
- **Test Pre-Join**: `./gradlew runTestPreJoin` (sample joined exports without MongoDB)
- **Clean**: `./gradlew clean`
- **Test**: `./gradlew test`

## Project Architecture

### Core Components

1. **ExportStrategy** (`src/main/java/com/example/mongoexport/ExportStrategy.java`)
   - Enum with two values: DENORMALIZED, DELIMITED
   - Controls how multi-value fields are exported

2. **ExportConfig** (`src/main/java/com/example/mongoexport/ExportConfig.java`)
   - Loads configuration from `application.properties`
   - Manages environment selection (dev/stage/prod)
   - Creates output directory automatically
   - Validates MongoDB connection strings

3. **MongoToCSVExporter** (`src/main/java/com/example/mongoexport/MongoToCSVExporter.java`)
   - Core export engine
   - Implements both export strategies
   - Handles multi-value field combinations
   - **Important**: DELIMITED strategy sorts array values alphabetically for consistency
   - Progress logging every 1000 documents
   - Supports nested field access (e.g., "address.city")

4. **Main** (`src/main/java/com/example/mongoexport/Main.java`)
   - Entry point with example exports
   - Currently configured to export: clients, products, users collections
   - Each export demonstrates different multi-value field configurations

5. **TestMain** (`src/main/java/com/example/mongoexport/TestMain.java`)
   - Standalone test demonstrating CSV output without MongoDB
   - Useful for testing export logic

6. **DatabaseAnalyzer** (`src/main/java/com/example/mongoexport/DatabaseAnalyzer.java`)
   - Analyzes all collections in the database
   - Reports document counts, field structures, and data types
   - Identifies potential foreign key relationships
   - Generates comprehensive analysis report in output directory

7. **PreJoinExporter** (`src/main/java/com/example/mongoexport/PreJoinExporter.java`)
   - Creates rich denormalized datasets by joining multiple collections
   - Three main export views:
     - **Complete Property Listings**: Joins listings + properties + agents + brokerages
     - **Transaction History**: Joins transactions + properties + listings + agents
     - **Agent Performance**: Aggregates agent metrics from listings and transactions
   - Handles nested documents and array fields gracefully

8. **TestPreJoinExporter** (`src/main/java/com/example/mongoexport/TestPreJoinExporter.java`)
   - Generates sample pre-joined CSV files without MongoDB
   - Shows the structure of joined exports with realistic test data

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

### DENORMALIZED Strategy
- Creates multiple rows when documents contain multi-value fields
- Generates all possible combinations for multiple multi-value fields
- Example: Document with 3 interests → 3 rows in CSV
- Use case: When you need separate rows for analysis/reporting

### DELIMITED Strategy  
- Creates one row per document
- Multi-value fields are sorted alphabetically then joined with commas
- **Sorting ensures**: Same values always produce identical output regardless of order in MongoDB
- Example: ["sports", "music", "travel"] → "music,sports,travel"
- Use case: When you need one row per entity with all values preserved

## Configuration Details

### application.properties Structure
```properties
mongodb.url.dev=mongodb://username:password@host:port/?authSource=admin
mongodb.url.stage=...
mongodb.url.prod=...
current.environment=dev
database.name=realm
output.directory=./output
export.strategy=DENORMALIZED
```

### Important Configuration Notes
- MongoDB URLs contain actual credentials (check application.properties)
- Output files are timestamped: `{collection}_{strategy}_{yyyyMMdd_HHmmss}.csv`
- CSV files use UTF-8 encoding with proper quote handling

## Known Issues and Considerations

1. **MongoDB Connection**: Current credentials in application.properties may not be accessible from all environments
2. **Memory Usage**: Large collections are processed with cursors to minimize memory
3. **Null Handling**: Empty/null multi-value fields are handled gracefully
4. **Performance**: Tested with progress logging, suitable for large datasets

## Common Tasks

### Adding a New Collection Export
1. Open `Main.java`
2. Create a new method following the pattern:
```java
private static void exportNewCollection(MongoToCSVExporter exporter) {
    List<String> multiValueFields = Arrays.asList("field1", "field2");
    List<String> fieldsToExport = Arrays.asList("_id", "name", "field1", "field2");
    exporter.exportCollection("collectionName", multiValueFields, fieldsToExport);
}
```
3. Call the method from main()

### Changing Export Strategy
- Edit `application.properties`: `export.strategy=DELIMITED` or `export.strategy=DENORMALIZED`
- Or create `application-local.properties` (gitignored) for local overrides

### Testing Without MongoDB
Run `./gradlew runTest` to see example CSV output without requiring database connection

### Using Cleaned Exporters
Cleaned versions of the Ultra exporters are available that remove meaningless fields and resolve foreign keys:
- `./gradlew runUltraListingsCleaned` - Listings with ~67 meaningless columns removed
- `./gradlew runUltraTransactionCleaned` - Transactions with foreign keys resolved and empty fields removed  
- `./gradlew runUltraAgentPerformanceCleaned` - Agent performance with cleaned fields

See `CLEANED_EXPORTER_CHANGES.md` for details on what was removed and why.

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
- Initial commit: ac53ba3