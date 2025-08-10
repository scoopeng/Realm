# Quick Start Guide - Realm MongoDB Exporter

## 🚀 The Two-Step Process for Clean Data

**Step 1:** Discover everything → **Step 2:** Export clean data

## The Complete Workflow: From Discovery to Clean Data

### Step 1: Automatic Discovery and Full Export
```bash
# Discovers ALL fields and relationships automatically, exports everything
./gradlew autoDiscover -Pcollection=listings
```

**What happens:**
1. Scans documents to discover ALL field paths
2. Identifies foreign keys and expands relationships
3. Exports with every discovered column (could be 300-500+ columns)
4. Generates: 
   - `listings_export_[timestamp].csv` - Full data with all columns
   - `listings_summary.json` - Statistics about each field
   - `listings_discovery_report.json` - What was discovered

### Step 2: Review What Was Found
```bash
# Check the discovery report
cat output/listings_discovery_report.json | jq '.totalFieldsDiscovered'

# See which fields are empty/sparse
cat output/listings_summary.json | jq '.fieldSummaries[] | select(.category == "ALWAYS_EMPTY") | .fieldName'

# Count meaningful vs empty fields
cat output/listings_summary.json | jq '[.fieldSummaries[] | .category] | group_by(.) | map({(.[0]): length})'
```

### Step 3: Export With Only Valid Columns
```bash
# Use the summary to export ONLY columns with data
./gradlew filteredExport -Pcollection=listings
```

**This gives you:**
- Only columns that have actual data (non-empty, non-sparse)
- Much smaller, cleaner CSV file
- Same complete data, just without the empty columns

## Command Summary

### Primary Commands (Use These!)

| Command | Purpose | Output | When to Use |
|---------|---------|--------|-------------|
| `./gradlew autoDiscover -Pcollection=listings` | Discover everything & export all | Full CSV + summaries | First run, complete analysis |
| `./gradlew filteredExport -Pcollection=listings` | Export using saved summaries | Clean CSV, only valid columns | After autoDiscover, for clean data |

### All Available Commands

```bash
# RECOMMENDED WORKFLOW
./gradlew autoDiscover -Pcollection=listings  # Full discovery + export
./gradlew filteredExport                       # Clean export without empty columns

# OTHER OPTIONS
./gradlew analyzeFields                        # Just analyze, no full expansion
./gradlew comprehensiveExport                  # Original comprehensive export
./gradlew exportListings                       # Original 192-column export
./gradlew smartExport                          # Legacy smart export

# SPECIFY COLLECTION
-Pcollection=listings                          # For listings
-Pcollection=transactions                      # For transactions  
-Pcollection=agents                           # For agents
```

## The Two-Step Process for Clean Data

### Step 1: Discovery (Run Once)
```bash
./gradlew autoDiscover -Pcollection=listings
```
- Takes 5-10 minutes
- Generates huge CSV with ALL possible columns
- Creates summary.json with field statistics

### Step 2: Filtered Export (Run Anytime)
```bash
./gradlew filteredExport -Pcollection=listings
```
- Takes 2-3 minutes
- Uses summary.json to exclude empty/sparse fields
- Generates clean CSV with only meaningful columns

## What Gets Filtered Out?

The filtered export removes:
- **ALWAYS_EMPTY** - Fields that are 100% null/empty
- **SINGLE_VALUE** - Fields with only one distinct value
- **SPARSE** - Fields that are >95% empty

Keeps:
- **MEANINGFUL** - Fields with actual varied data

## Example Workflow

```bash
# 1. Start fresh
rm -rf output/*

# 2. Discover and export everything (300+ columns)
./gradlew autoDiscover -Pcollection=listings
# Output: listings_export_20250806_100000.csv (50MB, 347 columns)

# 3. Check what was found
ls -lh output/
cat output/listings_summary.json | jq '.emptyFieldCount, .sparseFieldCount, .meaningfulFieldCount'
# Shows: 127 empty, 89 sparse, 131 meaningful

# 4. Get clean data (only meaningful columns)
./gradlew filteredExport
# Output: listings_export_20250806_101000.csv (25MB, 131 columns)
```

## File Outputs Explained

After `autoDiscover`:
```
listings_export_[timestamp].csv          # Full data, all columns
listings_summary.json                    # Field statistics  
listings_discovery_report.json           # Discovery details
```

After `filteredExport`:
```
listings_export_[timestamp].csv          # Clean data, only valid columns
listings_summary.json                    # Reused from discovery
```

## Memory Settings

If you run out of memory:
```bash
# Increase heap for large datasets
JAVA_OPTS="-Xmx24g" ./gradlew autoDiscover -Pcollection=listings

# Or edit build.gradle to change defaults
jvmArgs = ['-Xmx24g', '-Xms12g']
```

## Tips

1. **Run autoDiscover first** - It discovers everything automatically
2. **Use filteredExport for clean data** - Removes empty columns
3. **Collection names**: listings, transactions, agents, or any MongoDB collection
4. **Check summaries** - Review what fields were found before filtered export
5. **Rerun anytime** - If MongoDB schema changes, just run autoDiscover again

## Why This Approach?

- **No manual configuration** - Completely automatic discovery
- **Future proof** - New fields automatically included
- **Clean output** - Filtered export removes junk
- **Complete data** - Nothing missed, everything captured
- **Generic** - Works with any MongoDB collection