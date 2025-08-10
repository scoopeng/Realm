# Comprehensive Export System Guide

## Overview

The Comprehensive Export System is the latest evolution of the Realm MongoDB exporter, providing automatic relation expansion, intelligent field filtering, and real-time statistics collection.

## Quick Reference

### Primary Commands

```bash
# Analyze all collections and collect statistics
./gradlew analyzeFields

# Export with automatic filtering based on statistics
./gradlew filteredExport

# Full export with all features enabled
./gradlew comprehensiveExport

# Export with custom options
./gradlew comprehensiveExport -Pmode=full -Pcollection=listings
```

## How It Works

### 1. Automatic Relation Expansion

When you export a collection, the system automatically:
- Identifies all foreign key relationships
- Follows references to related collections
- Expands nested documents
- Includes all discovered fields in the export

Example for a listing:
```
listing document
  ├→ property (expanded)
  │   ├→ address details
  │   ├→ features
  │   └→ specifications
  ├→ currentAgent (expanded)
  │   ├→ person (expanded)
  │   │   ├→ contact info
  │   │   └→ residences
  │   └→ brokerage (expanded)
  └→ transactions (expanded)
      ├→ buyers (expanded)
      └→ sellers (expanded)
```

### 2. Field Statistics Collection

During export, the system tracks:
- **Null counts**: How often each field is empty
- **Unique values**: Number of distinct values
- **Value distribution**: For low-cardinality fields
- **Data types**: Inferred from actual values

Fields are categorized as:
- **MEANINGFUL**: Varied data, should be included
- **SPARSE**: >95% null, usually excluded
- **SINGLE_VALUE**: Only one value, can be excluded
- **ALWAYS_EMPTY**: 100% null, always excluded

### 3. Export Modes

#### Full Mode (Default)
```bash
./gradlew comprehensiveExport
```
- Expands all relationships
- Collects complete statistics
- Exports all fields
- Generates comprehensive metadata

#### Analyze Mode
```bash
./gradlew analyzeFields
```
- Scans collections without exporting
- Generates field statistics summaries
- Identifies fields to exclude
- Saves summaries for filtered exports

#### Filtered Mode
```bash
./gradlew filteredExport
```
- Uses saved summaries from analysis
- Excludes sparse/empty fields
- Includes only meaningful data
- Significantly smaller output files

#### Minimal Mode
```bash
java -cp build/libs/Realm-1.0-SNAPSHOT.jar \
  com.example.mongoexport.ComprehensiveExporter \
  minimal listings
```
- Basic export without enhancements
- No expansion or statistics
- Fastest option
- Smallest memory footprint

## Output Files

### CSV Data File
`{collection}_ultra_comprehensive_{timestamp}.csv`
- All data including expanded relations
- UTF-8 encoded
- Properly quoted fields
- Human-readable headers

### Field Summary
`{collection}_summary.json`
```json
{
  "collectionName": "listings",
  "totalDocuments": 64363,
  "fieldSummaries": [
    {
      "fieldName": "listPrice",
      "category": "MEANINGFUL",
      "nullPercentage": 2.3,
      "uniqueValues": 15234,
      "sampleValues": ["450000", "325000", "550000"],
      "dataType": "number"
    }
  ]
}
```

### Export Metadata
`{collection}_ultra_comprehensive_{timestamp}_metadata.json`
- Export configuration
- Performance metrics
- Column definitions
- Processing statistics

## Workflow Examples

### First-Time Export
```bash
# Start with a clean output directory
rm -rf output/*

# Analyze all collections
./gradlew analyzeFields

# Review summaries
cat output/listings_summary.json | jq '.fieldSummaries[] | select(.category == "ALWAYS_EMPTY")'

# Export with filtering
./gradlew filteredExport
```

### Regular Export
```bash
# If you already have summaries, just run filtered export
./gradlew filteredExport
```

### Deep Analysis
```bash
# Export with full expansion and statistics
./gradlew comprehensiveExport

# Analyze the metadata
cat output/listings_ultra_comprehensive_*_metadata.json | jq '.columns'
```

### Custom Configuration
```bash
# Adjust expansion depth
java -cp build/libs/Realm-1.0-SNAPSHOT.jar \
  com.example.mongoexport.ComprehensiveExporter \
  full listings --depth=3

# Specific sparse threshold
java -cp build/libs/Realm-1.0-SNAPSHOT.jar \
  com.example.mongoexport.ComprehensiveExporter \
  filtered listings --sparse-threshold=90
```

## Performance Tuning

### Memory Settings
Edit `build.gradle`:
```gradle
jvmArgs = ['-Xmx30g', '-Xms15g']  # Increase for larger datasets
```

### Expansion Depth
- Level 1: Direct foreign keys only
- Level 2: Foreign keys of foreign keys (default)
- Level 3+: Deeper relationships (use carefully)

### Collection Preloading
Small collections are automatically cached:
- brokerages, tags, teams, awards (< 1K docs)
- agents, currentAgents (< 50K docs)
- transactions, marketProfiles (< 100K docs)

Large collections use on-demand queries:
- properties (1.9M docs)
- people (620K docs) - cached if memory allows

## Troubleshooting

### Out of Memory
```
Error: Java heap space
```
Solution: Increase heap in build.gradle or reduce expansion depth

### Slow Performance
- Check MongoDB indexes on foreign key fields
- Reduce expansion depth
- Use filtered mode to reduce columns

### Missing Relationships
- Verify foreign key fields exist in documents
- Check RelationExpander configuration
- Increase expansion depth if needed

### Empty Output
- Ensure MongoDB connection is valid
- Check collection names are correct
- Verify user has read permissions

## Comparison with Original System

| Feature | Original Exporters | Comprehensive System |
|---------|-------------------|---------------------|
| Relation Expansion | Manual, limited | Automatic, complete |
| Field Discovery | Predefined | Dynamic from data |
| Field Filtering | Manual configuration | Automatic via statistics |
| Statistics | Basic counts | Comprehensive analysis |
| Export Modes | Single mode | Four distinct modes |
| Memory Usage | Fixed loading | Intelligent caching |
| Performance | Good | Similar or better |

## Best Practices

1. **Always analyze first**: Run `analyzeFields` before first export
2. **Use filtered mode**: Reduces file size without losing data
3. **Monitor memory**: Watch heap usage for large datasets
4. **Check summaries**: Review field categories before export
5. **Start clean**: Clear output directory for fresh analysis

## Advanced Usage

### Programmatic Access
```java
// Create custom export options
ExportOptions options = ExportOptions.builder()
    .enableRelationExpansion(true)
    .enableFieldStatistics(true)
    .expansionDepth(3)
    .excludeSparseColumns()
    .sparseThreshold(90.0)
    .build();

// Run enhanced exporter
EnhancedListingsExporter exporter = new EnhancedListingsExporter(options);
exporter.export();
```

### Adding New Relations
Edit `RelationExpander.java`:
```java
// Add new relationship
config.addRelation("myField", "targetCollection", RelationType.MANY_TO_ONE);
```

### Custom Field Categories
Modify `FieldStatisticsCollector.java`:
```java
// Adjust categorization logic
if (nullPercentage > 80) {  // Custom threshold
    return FieldCategory.SPARSE;
}
```

## Summary

The Comprehensive Export System provides intelligent, automatic handling of MongoDB exports with minimal configuration. It discovers relationships, analyzes field usage, and optimizes output automatically. Use `analyzeFields` first, then `filteredExport` for best results.