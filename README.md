# MongoDB Realm Data Export Project

A high-performance MongoDB data export utility designed for comprehensive real estate data extraction with optimized memory usage and processing speed.

## Project Status

**Current Version**: 1.0-SNAPSHOT  
**Last Updated**: 2025-08-03  
**Repository**: https://github.com/scoopeng/Realm

### Key Achievements
- ✅ Ultra-fast processing: ~2,000 listings/second
- ✅ Comprehensive exports: Up to 242 columns with denormalized indicators
- ✅ Memory-optimized: Configurable from 1GB to 20GB based on needs
- ✅ Multiple export strategies: From basic fields to full comprehensive data
- ✅ Batch processing: Efficient handling of large datasets (1.9M properties, 620K people)
- ✅ Complete database analysis with relationship mapping
- ✅ Production-ready exporters with optimized performance

## Quick Start

### Three Comprehensive Export Strategies

#### 1. Ultra Listings Export (Recommended)
```bash
./gradlew runUltraListings
```
- **Strategy**: Property/Listings-centric view with all related data
- **Output**: All 64K listings with 242 comprehensive columns
- **Features**: Property details, agent info, brokerage data, schools, amenities
- **Memory**: 20GB efficiently utilized
- **Performance**: ~2,200 listings/second
- **File Size**: ~76MB CSV

#### 2. Ultra Agent Performance Export
```bash
./gradlew runUltraAgentPerformance
```
- **Strategy**: Agent-centric view with performance metrics
- **Output**: All 28K agents with comprehensive performance data
- **Features**: Sales metrics, client data, geographic coverage, specializations
- **Memory**: 20GB with in-memory lookups
- **Performance**: Optimized batch processing
- **Use Case**: Agent analysis, performance tracking, market share

#### 3. Ultra Transaction History Export
```bash
./gradlew runUltraTransaction
```
- **Strategy**: Transaction-centric view with complete deal details
- **Output**: All 23K transactions with 128 comprehensive columns
- **Features**: Buyer/seller info, agent details, financing data, property specifics
- **Memory**: 20GB memory efficiently
- **Performance**: ~3,300 transactions/second  
- **File Size**: ~14MB CSV

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
| Ultra Listings | 64,363 | 242 | 32.7s | 20GB | 76MB | 2,200/sec |
| Ultra Agent Performance | 28,370 | 150+ | ~60s | 20GB | Variable | Batch optimized |
| Ultra Transaction History | 23,327 | 128 | 7.1s | 20GB | 14MB | 3,300/sec |

## Output Structure

All exports are organized in the `output/` directory:

```
output/
├── production_exports/     # Final comprehensive CSV exports
└── documentation/         # Database analysis documentation
```

## Final Export Files

The three comprehensive exports are stored in `output/production_exports/`:
- `all_listings_ultra_comprehensive_*.csv` - Complete listings with all joined data
- `agents_ultra_performance_*.csv` - Agent performance metrics and analytics  
- `transactions_ultra_comprehensive_*.csv` - Complete transaction history

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