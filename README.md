# Realm MongoDB Export Utility v2.0

A sophisticated two-phase MongoDB to CSV export system with intelligent field discovery, relationship expansion, and human-editable configuration.

## 🚀 Quick Start

```bash
# Phase 1: Discover all fields and create configuration
./gradlew discover -Pcollection=listings

# Phase 2: Export data using the configuration
./gradlew configExport -Pcollection=listings
```

The configuration file (`config/listings_fields.json`) can be edited between phases to customize the export.

## ✨ Key Features

### Two-Phase Workflow
- **Discovery Phase**: Analyzes your MongoDB collection to discover all fields, relationships, and statistics
- **Export Phase**: Uses the configuration to export exactly the fields you want

### Intelligent Field Discovery
- Automatically discovers all fields including nested documents and arrays
- Expands foreign key relationships up to 3 levels deep
- Collects statistics on field usage and distinct values
- Filters out empty and single-value fields automatically

### Human-Editable Configuration
- JSON configuration can be manually edited between phases
- Control which fields to include/exclude
- Customize business names for columns
- Configure array display (first value or comma-separated list)

### Smart Array Handling
- Automatically detects the best field to extract from array objects
- Sorts array values alphanumerically
- Configurable display modes per field

## 📋 Usage Examples

### Basic Two-Phase Export

```bash
# Step 1: Discover fields
./gradlew discover -Pcollection=listings

# Step 2: (Optional) Edit configuration
vi config/listings_fields.json

# Step 3: Export data
./gradlew configExport -Pcollection=listings
```

### Working with Different Collections

```bash
# Transactions
./gradlew discover -Pcollection=transactions
./gradlew configExport -Pcollection=transactions

# Agents
./gradlew discover -Pcollection=agents
./gradlew configExport -Pcollection=agents
```

### Legacy All-in-One Mode

```bash
# Original single-phase export (still available)
./gradlew autoDiscover -Pcollection=listings
```

## 📄 Configuration File

The discovery phase creates a JSON configuration file with this structure:

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
    }
  ],
  "requiredCollections": ["properties", "agents"],
  "exportSettings": {
    "batchSize": 5000,
    "useBusinessNames": true
  }
}
```

### Customizing the Configuration

- **Exclude a field**: Set `"include": false`
- **Change column name**: Edit `"businessName"`
- **Array display**: Change `"displayMode"` to `"first"` or `"comma_separated"`
- **Array field extraction**: Change `"extractField"` to any value from `"availableFields"`
- **See available options**: Check `"availableFields"` array to see all possible fields you can extract

## 🔧 Installation

### Prerequisites
- Java 11 or higher
- MongoDB connection
- 16GB+ RAM recommended

### Setup
1. Clone the repository
2. Configure MongoDB connection in `application.properties`:
   ```properties
   mongodb.url.dev=mongodb://username:password@host:port/?authSource=admin
   current.environment=dev
   database.name=realm
   ```
3. Build the project: `./gradlew build`

## 📊 Field Filtering Rules

The discovery phase automatically applies these intelligent rules:

| Rule | Description | Example |
|------|-------------|---------|
| **Include Business IDs** | Always included if they have data | mlsNumber, listingId, transactionId |
| **Exclude Technical IDs** | Always excluded | _id, __v, fields ending with Id |
| **Exclude Empty Fields** | 0 distinct non-null values | Fields that are always null |
| **Exclude Single-Value** | Only 1 distinct value | Fields like status="active" everywhere |
| **Include Multi-Value** | 2+ distinct values | Normal data fields |

## ✅ Testing Checklist

### Basic Workflow Testing
```bash
# 1. Compile the project
./gradlew build

# 2. Run discovery
./gradlew discover -Pcollection=listings

# 3. Check configuration file
cat config/listings_fields.json | jq . | head -50

# 4. Run export
./gradlew configExport -Pcollection=listings

# 5. Verify output
ls -lh output/*.csv
```

### Configuration Editing Test
```bash
# 1. Edit configuration
vi config/listings_fields.json
# - Set some fields to "include": false
# - Change some businessName values
# - Modify array displayMode settings

# 2. Re-run export
./gradlew configExport -Pcollection=listings

# 3. Verify changes in output
```

### Advanced Testing
- [ ] Test with all collections (listings, transactions, agents)
- [ ] Compare with legacy autoDiscover output
- [ ] Test with sparse collections
- [ ] Verify relationship expansion
- [ ] Check array field handling

## 🚨 Troubleshooting

### Common Issues and Solutions

| Issue | Solution |
|-------|----------|
| **Discovery fails** | Check MongoDB URL in `application.properties` |
| **No config file** | Ensure discovery completed successfully |
| **Missing fields** | Check `include` flag in JSON configuration |
| **Memory errors** | Increase heap in build.gradle: `-Xmx24g` |
| **Empty arrays** | Verify `extractField` in array configuration |

### Debug Commands

```bash
# Check logs
tail -f logs/application.log

# Verify MongoDB connection
mongo $MONGO_URL --eval "db.listings.count()"

# Check configuration
jq '.fields[] | select(.include==false)' config/listings_fields.json
```

## ⚡ Performance

- **Discovery Phase**: ~2-3 minutes for 10,000 document sample
- **Export Phase**: 3,500-5,000 documents/second
- **Memory Usage**: 16-24GB heap recommended
- **Collection Caching**: Auto-caches collections <100K documents

## 📁 Project Structure

```
src/main/java/com/example/mongoexport/
├── config/                        # Configuration classes
│   ├── FieldConfiguration.java    # Individual field metadata
│   └── DiscoveryConfiguration.java # Root configuration
├── discovery/
│   └── FieldDiscoveryService.java # Field discovery logic
├── export/
│   └── ConfigurationBasedExporter.java # Config-based export
├── DiscoveryRunner.java          # Discovery entry point
├── ConfigExportRunner.java        # Export entry point
└── AutoDiscoveryExporter.java     # Legacy all-in-one

config/                            # JSON configurations
└── {collection}_fields.json      # Per-collection config

output/                            # Export results
└── {collection}_ultra_comprehensive_{timestamp}.csv
```

## 🔄 Version History

### v2.0 (Current) - Two-Phase Workflow
- Separated discovery and export phases
- Human-editable JSON configuration
- Enhanced array field handling
- Improved collection caching

### v1.0 - Auto-Discovery
- Single-phase automatic discovery and export
- Intelligent field filtering
- Relationship expansion

## 📝 License

Private repository - Internal use only

## 🆘 Support

For issues or questions:
1. Check the testing checklist above
2. Review CLAUDE.md for detailed documentation
3. Contact the development team