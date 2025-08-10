# Realm MongoDB Exporter v2.0

A powerful, intelligent MongoDB to CSV export utility that automatically discovers fields, applies smart filtering, and uses business-readable column names.

## ✨ Key Features

- **Automatic Field Discovery** - No hardcoded schemas, discovers all fields dynamically
- **Intelligent Filtering** - Exports only meaningful fields (>2 distinct values)
- **Business-Readable Names** - Converts MongoDB field paths to friendly column names
- **Universal Compatibility** - Works with ANY MongoDB collection
- **Future-Proof** - Automatically adapts to schema changes
- **High Performance** - Processes 3,500-20,000 records/second

## 🚀 Quick Start

```bash
# The one command you need:
./gradlew autoDiscover -Pcollection=listings
```

This single command will:
1. Connect to your MongoDB database
2. Discover all fields in the collection
3. Filter out empty/sparse fields (keeping only those with >2 distinct values)
4. Convert field names to business-readable format
5. Export clean, analytics-ready CSV

## 📋 Primary Commands

| Command | Description | Use Case |
|---------|-------------|----------|
| `autoDiscover` | **★ RECOMMENDED** - Auto-discovers fields, filters intelligently, uses business names | Best for most exports |
| `filteredExport` | Maximum filtering - removes all sparse/empty columns | Cleanest data |
| `fullExport` | Complete export with relationship expansion | When you need everything |
| `analyzeFields` | Field analysis only, no export | Understanding your data |

## 📚 Examples

```bash
# Export listings with intelligent filtering (recommended)
./gradlew autoDiscover -Pcollection=listings

# Export transactions with maximum filtering
./gradlew filteredExport -Pcollection=transactions

# Analyze agents collection without exporting
./gradlew analyzeFields -Pcollection=agents

# Export all major collections
./gradlew autoDiscover -Pcollection=all

# Export any MongoDB collection
./gradlew autoDiscover -Pcollection=yourCollectionName
```

## 📂 Installation

```bash
git clone https://github.com/scoopeng/Realm.git
cd Realm
./gradlew build
```

### Prerequisites
- Java 11 or higher
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

## 🏗️ Architecture

### Clean, Modular Design

```
AutoDiscoveryExporter (Main Engine)
    ├── Automatic field discovery
    ├── Intelligent filtering (>2 distinct values)
    ├── Business name mapping
    └── Generic for any collection

Supporting Components:
    ├── FieldNameMapper - MongoDB paths → business names
    ├── AbstractUltraExporter - Base export functionality
    ├── FieldStatisticsCollector - Field analysis
    └── RelationExpander - Foreign key expansion
```

### Why This Design?

1. **No Hardcoding** - Works with any collection without code changes
2. **Smart Defaults** - Automatically filters out useless columns
3. **Readable Output** - Business users understand the column names
4. **Maintainable** - Single implementation for all collections
5. **Extensible** - Easy to add new field mappings

## 📊 Output Files

Each export generates:
- `{collection}_export_{timestamp}.csv` - The data with filtered, readable columns
- `{collection}_discovery_report.json` - Complete field analysis
- `{collection}_summary.json` - Export statistics

Example output structure:
```
output/
├── listings_export_20250806_100000.csv         # 17MB, 50 columns (filtered)
├── listings_discovery_report.json              # Field analysis details
└── listings_summary.json                       # Export statistics
```

## 🔍 How It Works

### The AutoDiscovery Process

1. **Field Discovery Phase**
   - Scans sample documents (1000 by default)
   - Identifies all field paths including nested fields
   - Tracks distinct values for each field

2. **Filtering Phase**
   - Keeps fields with >2 distinct values
   - Always includes important fields (IDs, dates, prices)
   - Excludes empty, single-value, and sparse fields

3. **Export Phase**
   - Maps field paths to business-readable names
   - Exports only the filtered, meaningful columns
   - Generates comprehensive reports

### Business Name Mapping Examples

| MongoDB Path | Business Name |
|-------------|---------------|
| `_id` | Record ID |
| `listPrice` | List Price |
| `property.bedrooms` | Property Bedrooms |
| `listingAgent.email` | Listing Agent Email |
| `fees[].feeAmount` | Fee Amount |
| `dateListed` | Date Listed |

## 🛠️ Advanced Usage

### Custom Filtering Thresholds

Modify `AutoDiscoveryExporter.java`:
```java
private final int minDistinctValues = 3; // Change threshold
```

### Adding Custom Field Mappings

Edit `FieldNameMapper.java`:
```java
FIELD_MAPPINGS.put("yourFieldPath", "Your Business Name");
```

### Memory Settings

For large collections:
```bash
JAVA_OPTS="-Xmx24g" ./gradlew autoDiscover -Pcollection=listings
```

## 📈 Performance Benchmarks

| Collection | Documents | Export Time | Rate | Output Size |
|-----------|-----------|-------------|------|-------------|
| Listings | 64K | ~9 seconds | 7,000/sec | 17MB (filtered) |
| Agents | 28K | ~1.5 seconds | 20,000/sec | 8MB (filtered) |
| Transactions | 23K | ~4 seconds | 5,600/sec | 12MB (filtered) |

## 🔄 Migration from v1.0

If you were using the old hardcoded exporters:

| Old Command | New Command |
|------------|-------------|
| `exportListings` | `autoDiscover -Pcollection=listings` |
| `exportTransactions` | `autoDiscover -Pcollection=transactions` |
| `exportAgents` | `autoDiscover -Pcollection=agents` |
| `exportUltraComprehensive` | `fullExport -Pcollection=all` |

## 📝 Project Structure

```
src/main/java/com/example/mongoexport/
├── AutoDiscoveryExporter.java    # Main export engine
├── FieldNameMapper.java          # Business name mappings
├── ComprehensiveExporter.java    # CLI entry point
├── AbstractUltraExporter.java    # Base functionality
├── FieldStatisticsCollector.java # Field analysis
├── RelationExpander.java         # Relationship handling
├── ExportConfig.java             # Configuration
├── ExportOptions.java            # Export settings
└── SmartExporter.java            # Legacy CLI (retained)
```

## 🐛 Troubleshooting

### Out of Memory Error
Increase heap size:
```bash
JAVA_OPTS="-Xmx32g" ./gradlew autoDiscover -Pcollection=listings
```

### Connection Issues
Check MongoDB URL in `application.properties` and ensure network connectivity.

### Empty Output
Run `analyzeFields` first to understand the data structure.

## 🤝 Contributing

Contributions welcome! The codebase is now clean and modular:
- Single exporter handles all collections
- Easy to extend field mappings
- Clear separation of concerns

To contribute:
1. Fork the repository
2. Create a feature branch
3. Add field mappings to `FieldNameMapper.java` if needed
4. Submit a pull request

## 📜 License

MIT License - See LICENSE file for details

## 🎯 Philosophy

This project embodies the principle: **"Smart defaults, zero configuration, maximum value"**

The AutoDiscoveryExporter eliminates the need for manual schema management while providing clean, business-ready data exports.

## 📧 Support

For issues or questions:
- Create an issue on [GitHub](https://github.com/scoopeng/Realm/issues)
- Check existing documentation in `/docs` folder

---

*Built with ❤️ for data engineers who value clean, maintainable code*