# Realm MongoDB Exporter v2.0

A powerful, intelligent MongoDB to CSV export utility that automatically discovers fields, applies smart filtering, and uses business-readable column names.

## ✨ Key Features

- **Automatic Field Discovery** - No hardcoded schemas, discovers all fields dynamically
- **Intelligent Filtering** - Exports only meaningful fields (≥2 distinct non-null values)
- **Business-Readable Names** - Converts MongoDB field paths to friendly column names
- **Relationship Expansion** - Automatically expands foreign keys up to 3 levels deep
- **Business ID Preservation** - Keeps mlsNumber, listingId, transactionId, etc.
- **Universal Compatibility** - Works with ANY MongoDB collection
- **Future-Proof** - Automatically adapts to schema changes
- **High Performance** - Processes 3,500-5,000 records/second with expansion

## 🚀 Quick Start

```bash
# The ONE command you need:
./gradlew autoDiscover -Pcollection=listings
```

This single command will:
1. Pre-cache frequently referenced collections
2. Discover ALL fields by sampling 10,000 documents
3. Expand foreign key relationships up to 3 levels deep
4. Filter out fields with <2 distinct non-null values
5. Preserve business-critical IDs (mlsNumber, listingId, etc.)
6. Map field paths to business-readable column names
7. Export clean, analytics-ready CSV

## 📋 Command Line Usage

### Primary Command (Use This!)

```bash
# Export any collection with automatic discovery:
./gradlew autoDiscover -Pcollection=listings
./gradlew autoDiscover -Pcollection=transactions
./gradlew autoDiscover -Pcollection=agents
./gradlew autoDiscover -Pcollection=all        # Exports all three
```

### Command Aliases (All map to autoDiscover)

```bash
./gradlew export -Pcollection=listings         # Alias for autoDiscover
./gradlew discover -Pcollection=listings       # Alias for autoDiscover
./gradlew filteredExport -Pcollection=listings # Alias for autoDiscover
./gradlew fullExport -Pcollection=listings     # Alias for autoDiscover
./gradlew analyzeFields -Pcollection=listings  # Alias for autoDiscover
```

## 📂 Installation

```bash
git clone https://github.com/scoopeng/Realm.git
cd Realm
./gradlew build
```

### Prerequisites
- Java 21 or higher
- MongoDB connection with credentials
- 16-20GB RAM recommended for large exports

### Configuration
Edit `src/main/resources/application.properties`:
```properties
mongodb.url.dev=mongodb://username:password@host:port/?authSource=admin
current.environment=dev
database.name=realm
output.directory=./output
```

## 🏗️ Architecture (Post-Cleanup)

### Single Clean Implementation

```
AutoDiscoveryExporter (Single Source of Truth)
├── AbstractUltraExporter (Base Infrastructure)
├── RelationExpander (Relationship Handling)
├── FieldStatisticsCollector (Statistics)
├── FieldNameMapper (Business Names)
└── ExportOptions (Configuration)
```

### What Was Cleaned Up

**Deleted Files** (690 lines removed):
- SmartExporter.java
- FieldScanner.java
- ExportMetadata.java

**Simplified Files** (524 lines removed):
- AbstractUltraExporter.java
- AutoDiscoveryExporter.java
- ComprehensiveExporter.java
- ExportOptions.java
- build.gradle

**Result**: ~1,214 lines of code removed, ONE clear implementation path

## 📊 How It Works

### Phase 1: Pre-Caching
- Loads small collections entirely into memory
- Caches only referenced properties from large collections
- Optimizes lookup performance

### Phase 2: Field Discovery
- Samples 10,000 documents from target collection
- Discovers ALL fields including nested and arrays
- Tracks occurrence counts and distinct values

### Phase 3: Relationship Discovery
- Identifies ObjectId references
- Uses RelationExpander to follow foreign keys
- Discovers fields in related documents

### Phase 4: Field Filtering
- **Excludes**: Fields with 0 or 1 distinct non-null values
- **Preserves**: Business IDs (mlsNumber, listingId, transactionId)
- **Keeps**: Binary fields (true/false values)
- **Removes**: Technical fields (_id, __v, @reference)

### Phase 5: Export
- Processes documents in batches of 1,000
- Expands relationships using cached data
- Writes CSV with business-readable headers

## 📄 Output Files

Each export generates three files in `./output/`:

1. **CSV Data File**: `{collection}_full_{timestamp}.csv`
   - Filtered, meaningful columns only
   - Business-readable headers
   - RFC 4180 compliant format

2. **Discovery Report**: `{collection}_discovery_report.json`
   ```json
   {
     "collection": "listings",
     "sampleSize": 10000,
     "totalFieldsDiscovered": 487,
     "fieldsIncluded": 192,
     "fieldsExcluded": 295,
     "fields": [
       {
         "path": "mlsNumber",
         "businessName": "MLS Number",
         "occurrences": 10000,
         "uniqueValues": 9987,
         "included": true,
         "reason": "Business ID"
       }
     ]
   }
   ```

3. **Export Summary**: `{collection}_summary.json`
   - Processing statistics
   - Performance metrics
   - Field categories

## 🎯 Business Rules

### Fields Always Preserved (Business IDs)
- mlsNumber
- listingId
- transactionId
- orderId
- contractNumber
- referenceNumber
- confirmationNumber
- invoiceNumber
- accountNumber
- caseNumber
- ticketId

### Fields Always Excluded
- Fields ending with "_id" (except business IDs)
- Fields containing "__v"
- Fields containing "@reference"
- Fields with only 1 distinct non-null value
- Fields with >95% null values

### Expansion Depth
- Default: 3 levels deep
- Example: listing → property → address → coordinates

## 📈 Performance Characteristics

| Phase | Speed | Memory Usage |
|-------|-------|--------------|
| Discovery | ~10,000 docs/sec | Low |
| Expansion | ~1,000 docs/sec | High (caching) |
| Export | ~3,500-5,000 docs/sec | Moderate |

### Collection Statistics
- **Properties**: 1.9M documents (selectively cached)
- **Listings**: 64K active documents
- **Transactions**: 23K documents
- **Agents**: 28K documents

## 🛠️ Troubleshooting

### Out of Memory Error
```bash
# Increase heap size in build.gradle:
jvmArgs = ['-Xmx24g', '-Xms12g']
```

### Slow Export
- Check MongoDB network latency
- Ensure indexes exist on referenced collections
- Run during off-peak hours

### Missing Expected Fields
- Check discovery report for exclusion reasons
- Field may have <2 distinct values
- Verify field exists in sampled documents

### Connection Issues
- Verify MongoDB credentials
- Check authSource parameter
- Test connection with MongoDB client

## 📝 Project Structure (Current)

```
src/main/java/com/example/mongoexport/
├── AutoDiscoveryExporter.java    # Main export engine (1,031 lines)
├── AbstractUltraExporter.java    # Base functionality (332 lines)
├── RelationExpander.java         # Relationship handling (392 lines)
├── FieldStatisticsCollector.java # Field analysis (235 lines)
├── FieldNameMapper.java          # Business names (326 lines)
├── ComprehensiveExporter.java    # CLI entry point (106 lines)
├── ExportConfig.java             # Configuration (62 lines)
└── ExportOptions.java            # Export settings (75 lines)
```

## 🔄 Recent Changes (Master Cleanup)

### What Changed
- **Removed**: 3 unused files (690 lines)
- **Simplified**: 5 core files (524 lines removed)
- **Result**: Single implementation path, no duplicate logic

### Key Improvements
1. AutoDiscoveryExporter is the single source of truth
2. All collections use identical processing logic
3. Business IDs properly preserved
4. RelationExpander properly integrated
5. Consistent 3-level expansion depth

## 📜 License

MIT License - See LICENSE file for details

## 🎯 Philosophy

**"One way to do everything, and that way works perfectly"**

The cleaned-up codebase eliminates confusion by providing a single, robust implementation that handles all export scenarios automatically.

---

*Clean code is not written by following a set of rules. You know you are working on clean code when each routine you read turns out to be pretty much what you expected.*