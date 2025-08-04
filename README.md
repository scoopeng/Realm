# MongoDB Realm Data Export Project

A high-performance MongoDB data export utility designed for comprehensive real estate data extraction with optimized memory usage and processing speed.

## Project Status

**Current Version**: 2.0-SNAPSHOT  
**Last Updated**: 2025-08-04  
**Repository**: https://github.com/scoopeng/Realm

### Key Achievements
- ✅ Ultra-fast processing: Up to 20,000 agents/second, 5,600 transactions/second, 3,500 listings/second
- ✅ Human-readable exports: All columns have user-friendly headers
- ✅ Optimized data quality: Removed ~100+ meaningless columns across all exporters
- ✅ Foreign key resolution: All IDs resolved to human-readable names
- ✅ Memory-optimized: 20GB heap allocation for optimal performance
- ✅ Clean codebase: Removed all dead code and test utilities
- ✅ Production-ready: Three comprehensive exporters with complete documentation

## Quick Start

### Three Comprehensive Export Strategies

#### 1. Ultra Listings Export (Recommended)
```bash
./gradlew runUltraListings
```
- **Strategy**: Property/Listings-centric view with all related data
- **Output**: All 64K listings with ~192 human-readable columns
- **Features**: Property details, agent info, brokerage data, schools, amenities, market data
- **Quality Improvements**: Removed ~67 meaningless columns, fixed brokerage city extraction
- **Performance**: ~3,500 listings/second
- **File Size**: ~92MB CSV with human-readable headers

#### 2. Ultra Agent Performance Export
```bash
./gradlew runUltraAgentPerformance
```
- **Strategy**: Agent-centric view with performance metrics
- **Output**: All 28K agents with ~150 human-readable columns
- **Features**: Sales metrics, client data, geographic coverage, specializations, team info
- **Quality Improvements**: Resolved all foreign keys, removed meaningless fields
- **Performance**: ~20,000 agents/second
- **File Size**: ~21MB CSV
- **Use Case**: Agent analysis, performance tracking, recruitment

#### 3. Ultra Transaction History Export
```bash
./gradlew runUltraTransaction
```
- **Strategy**: Transaction-centric view with complete deal details
- **Output**: All 23K transactions with ~120 human-readable columns
- **Features**: Buyer/seller info, agent details, financing data, property specifics
- **Quality Improvements**: Replaced ObjectIds with names, removed empty features
- **Performance**: ~5,600 transactions/second  
- **File Size**: ~9MB CSV

## Architecture

The three exporters implement distinct analytical perspectives on the real estate database:

1. **Listings-Centric**: Complete property and listing information with agent/brokerage details
2. **Agent-Centric**: Agent performance metrics with aggregated sales and client data  
3. **Transaction-Centric**: Complete transaction history with all parties and deal specifics

All exporters use optimized memory management:
- Small collections (agents, brokerages, people) loaded entirely into memory
- Large collections (listings, properties, transactions) processed in batches
- In-memory lookups eliminate individual database queries during processing

## Prerequisites

- Java 11 or higher
- MongoDB instance
- Gradle (wrapper included)

## Configuration

1. Copy the example configuration:
   ```bash
   cp src/main/resources/application.properties src/main/resources/application-local.properties
   ```

2. Edit `application-local.properties` and update MongoDB credentials:
   ```properties
   # Replace placeholders with actual values
   mongodb.url.dev=mongodb://username:password@localhost:27017/?authSource=admin
   mongodb.url.stage=mongodb://username:password@stage-host:27017/?authSource=admin
   mongodb.url.prod=mongodb://username:password@prod-host:27017/?authSource=admin
   
   # Set your current environment
   current.environment=dev
   
   # Configure database name
   database.name=your_database_name
   
   # Set export strategy (DENORMALIZED or DELIMITED)
   export.strategy=DENORMALIZED
   ```

## Performance Benchmarks

| Export Strategy | Records | Columns | Time | Memory | Output Size | Performance |
|----------------|---------|---------|------|---------|-------------|-------------|
| Ultra Listings | 64,363 | ~192 | 18.3s | 20GB | 92MB | 3,500/sec |
| Ultra Agent Performance | 28,370 | ~150 | 1.4s | 20GB | 21MB | 20,000/sec |
| Ultra Transaction History | 23,327 | ~120 | 4.2s | 20GB | 9MB | 5,600/sec |

## Output Structure

All exports are organized in the `output/` directory:

```
output/
├── production_exports/     # Final comprehensive CSV exports
└── documentation/         # Database analysis documentation
```

## Final Export Files

The three comprehensive exports are stored in `output/`:
- `all_listings_ultra_comprehensive_*.csv` - Complete listings with all joined data
- `agent_performance_ultra_comprehensive_*.csv` - Agent performance metrics and analytics  
- `transaction_history_ultra_comprehensive_*.csv` - Complete transaction history

## Export Strategies

### DENORMALIZED Strategy
Creates multiple rows when a document has multi-value fields.

Example input document:
```json
{
  "_id": "123",
  "name": "John Doe",
  "email": "john@example.com",
  "interests": ["sports", "music", "travel"]
}
```

Output CSV:
```csv
_id,name,email,interests
123,John Doe,john@example.com,sports
123,John Doe,john@example.com,music
123,John Doe,john@example.com,travel
```

### DELIMITED Strategy
Creates one row per document with multi-values joined by commas. **Values are sorted alphabetically for consistency**.

Example input document:
```json
{
  "_id": "123",
  "name": "John Doe",
  "email": "john@example.com",
  "interests": ["sports", "music", "travel"]
}
```

Output CSV (interests sorted alphabetically):
```csv
_id,name,email,interests
123,John Doe,john@example.com,"music,sports,travel"
```

This sorting ensures that documents with the same multi-value elements always produce identical CSV output, regardless of the order in MongoDB.

## Customizing Exports

To export different collections or fields, modify the `Main.java` file:

```java
private static void exportCustomCollection(MongoToCSVExporter exporter) {
    List<String> multiValueFields = Arrays.asList("tags", "categories");
    List<String> fieldsToExport = Arrays.asList(
        "_id", 
        "title", 
        "description", 
        "tags", 
        "categories"
    );
    
    exporter.exportCollection("mycollection", multiValueFields, fieldsToExport);
}
```

## Output Files

CSV files are saved to the `./output` directory with the naming convention:
```
{collection}_{strategy}_{timestamp}.csv
```

Example: `clients_denormalized_20241201_143052.csv`

## Logging

The application uses SLF4J with Logback for logging. Logs include:
- Connection status
- Export progress
- Document counts
- Error messages

## Error Handling

The application includes comprehensive error handling for:
- MongoDB connection failures
- Missing collections
- File I/O errors
- Invalid configuration

## Performance Considerations

- Documents are processed in batches with progress logging every 1000 documents
- Uses try-with-resources for proper resource management
- Efficient memory usage with cursor-based iteration

## License

This project is proprietary software.