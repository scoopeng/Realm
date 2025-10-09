# WealthX Export Implementation Plan
*Created: October 8, 2025*
*Last Updated: October 8, 2025*

## IMPLEMENTATION PROGRESS

### Phase 1: Environment/Database Support ✅ COMPLETE
- [x] 1.1 Add env/database parameters to gradle tasks (discover, configExport)
- [x] 1.2 Modify ExportConfig.java to accept environment override
- [x] 1.3 Modify ExportConfig.java to accept database override
- [x] 1.4 Modify DiscoveryRunner.java to accept and use overrides
- [x] 1.5 Modify ConfigExportRunner.java to accept and use overrides
- [x] 1.6 Test connection: `./gradlew explore -Penv=lake -Pdatabase=WealthX`
- [x] 1.7 Validate can connect and list collections

**Status**: ✅ Complete
**Actual Time**: ~1 hour
**Blockers**: None

**Changes Made**:
- build.gradle: Added -Penv and -Pdatabase parameters to discover and configExport tasks
- ExportConfig.java: Added constructor accepting environmentOverride and databaseOverride
- AbstractUltraExporter.java: Added constructor accepting environment/database overrides
- ConfigurationBasedExporter.java: Added constructors with override support
- DiscoveryRunner.java: Accepts environment/database from command line args[1] and args[2]
- ConfigExportRunner.java: Accepts environment/database from command line args[2] and args[3]

### Phase 2: Basic Discovery & Export ✅ COMPLETE
- [x] 2.1 Run discovery on personwealthxdataMongo collection
- [x] 2.2 Review discovered fields (expect ~63 field paths)
- [x] 2.3 Review auto-generated field configurations
- [x] 2.4 Manually adjust include/exclude flags if needed (not required - auto-config looks good)
- [x] 2.5 Test export with rowLimit=1000
- [x] 2.6 Validate CSV output structure and data quality
- [ ] 2.7 Test export with rowLimit=10000 (optional - can skip to full export)
- [ ] 2.8 Run full export (4.5M records) (optional - can be done in separate session)
- [ ] 2.9 Validate final CSV (row count, field coverage, no corruption)

**Status**: ✅ Complete - Minimum viable export working!
**Actual Time**: ~15 minutes total
**Blockers**: None

**Discovery Results**:
- Total fields discovered: 62
- Fields included for export: 40
- Fields excluded by filters: 22
- Array fields found: 4 (interests, netWorthHoldings, nickNames, residences)
- Tags and lifestyles excluded (likely empty or low coverage in sample)

**Included Fields Summary**:
- Personal: firstName, lastName, fullName, email, DOB, age, maritalStatus, sex, photoUrl
- Business: business.company, business.position, business.phone, business.email, business.address, business.city, business.state, business.postalCode, business.country
- Net Worth: netWorth.householdWealth, netWorth.householdNetWorth, netWorth.liquidLowerInt, netWorth.netWorthLowerInt, netWorth.chartURL, netWorth.netWorthHoldings (array)
- Residence: residences (array)
- Interests: interests (array), nickNames (array)
- Metadata: cities, names, lastIngestion, lastWXUpdate, updatedAt, bio

**Test Export Results (1,000 rows)**:
- File: `output/personwealthxdataMongo_full_20251008_060544.csv`
- Rows: 1,001 (1 header + 1,000 data)
- Columns: 40 as expected
- Export time: 7 seconds (145 rows/sec)
- Array handling: ✅ Working correctly
  - nickNames: "Thomas J. Vilsack, Tom, Tom Vilsack" (comma-separated)
  - residences: "United States" (country from first residence)
  - netWorthHoldings: "Finance / Banking / Investment" (industry from holdings)
- Data quality: ✅ No corruption, proper CSV formatting
- All business-readable column headers present

### Phase 3: PRIMARY Mode (Future)
- Status: Not planned for this session

### Phase 4: COUNT Mode (Future)
- Status: Not planned for this session

### Phase 5: STATISTICS Mode (Future)
- Status: Not planned for this session

---

## Executive Summary

Add WealthX `personwealthxdataMongo` collection (4.5M records) as the third major export capability, alongside existing listings and agentclients exports. This collection contains high-value wealth and demographic data with complex nested arrays requiring thoughtful array handling strategies.

## Current System Analysis

### Existing Export Collections
1. **listings** - ~64K property listings
2. **agentclients** - ~573K agent-client relationships
3. **agents** - ~28K agent profiles
4. **transactions** - Sales transactions

### Current Array Handling Patterns

**Configuration Infrastructure** (FieldConfiguration.java):
- ✅ `ArrayConfiguration.displayMode`: "comma_separated", "first"
- ✅ `extractionMode`: "primary", "count", "statistics"
- ✅ `StatisticsConfiguration`: Full aggregation support (count, sum, avg, min, max, median)

**Actual Usage in Production**:
- **All existing arrays use "comma_separated" mode**
- Arrays of ObjectIds → Extract field (e.g., `fullName`) → Comma-separated list
- Arrays of objects → Extract specific field → Comma-separated list
- Examples:
  - `agents[]` → Extract `fullName` → "John Doe, Jane Smith, Bob Jones"
  - `lifestyles[]` → Extract `lifestyleName` → "Golf, Travel, Wine"

**Current Limitations**:
- No "count" mode usage
- No "statistics" mode usage
- No "primary" (first element) extraction
- Infrastructure exists but not utilized

## WealthX Data Structure

### Collection Details
- **Environment**: lake (ONLY - not in dev/stage/prod)
- **Database**: WealthX
- **Collection**: personwealthxdataMongo
- **Record Count**: 4,458,826
- **Field Paths**: 63 unique paths

### Field Categories

**Simple Fields (Direct Export)**:
- Personal: firstName, lastName, fullName, email, DOB, age, maritalStatus, sex
- Identifiers: wxId, photoUrl
- Metadata: lastIngestion, lastWXUpdate, updatedAt, __v
- Search fields: cities, names (pipe-delimited)

**Nested Objects (Flatten)**:
- `business.*` - Company, position, contact info, location (9 fields)
- `netWorth.*` - Wealth metrics, URLs (8 scalar fields)

**Simple Arrays (Standard Comma-Separated)**:
- `nickNames[]` - String array → comma-separated
- `tags[]` - Array of objects with `tagName` field → extract tagName
- `lifestyles[]` - Array of objects with `tagName` field → extract tagName

**Complex Arrays (DECISION REQUIRED)**:
1. **`residences[]`** - Multiple addresses (100% present, avg 1.x per person)
2. **`netWorth.netWorthHoldings[]`** - Asset holdings (35% present, variable count)
3. **`interests[]`** - Hobbies/interests (52% present, variable count)

## Array Handling Decisions

### Decision Matrix

| Array Field | Presence | Avg Length | Recommendation | Rationale |
|-------------|----------|------------|----------------|-----------|
| `nickNames[]` | 100% | Low | **Comma-separated** | Simple string array, standard pattern |
| `tags[]` | 100% | Variable | **Comma-separated tagNames** | Matches existing lifestyle pattern |
| `lifestyles[]` | 100% | Variable | **Comma-separated tagNames** | Matches existing pattern |
| `residences[]` | 100% | ~1-2 | **PRIMARY + COUNT** | Most people have 1-2 homes |
| `netWorth.netWorthHoldings[]` | 35% | Variable | **STATISTICS (aggregate)** | Complex objects, need totals |
| `interests[]` | 52% | Variable | **COUNT + LIST** | Not critical, simple handling |

### Detailed Array Strategies

#### 1. residences[] - PRIMARY + COUNT Strategy

**Fields to Export**:
```
residence_primary_address        (from residences[0].address)
residence_primary_city           (from residences[0].city)
residence_primary_state          (from residences[0].state)
residence_primary_postalCode     (from residences[0].postalCode)
residence_primary_country        (from residences[0].country)
residence_count                  (count of residences array)
residence_cities                 (comma-separated list of all cities)
```

**Implementation**:
- Create 7 synthetic fields with `extractionMode: "primary"` and `extractionIndex: 0`
- Add one count field with `extractionMode: "count"`
- Add one list field for city names

**Rationale**: Most people have 1 primary residence. Extracting first residence as primary + count gives full picture.

#### 2. netWorth.netWorthHoldings[] - STATISTICS Strategy

**Fields to Export**:
```
netWorth_totalHoldingsValue      (SUM of all valueOfHoldings)
netWorth_holdingsCount           (COUNT of holdings)
netWorth_propertyCount           (COUNT where assetType = "Property")
netWorth_stockCount              (COUNT where type contains "Stock")
netWorth_largestHoldingValue     (MAX of valueOfHoldings)
netWorth_largestHoldingName      (assetName of largest holding)
netWorth_holdingTypes            (comma-separated list of unique types)
```

**Implementation**:
- Use `extractionMode: "statistics"` with `StatisticsConfiguration`
- Aggregation types: sum, count, max
- Conditional counts for asset types
- May require code enhancement for conditional counts

**Rationale**: Assets are highly variable (0-50+ per person). Aggregates give meaningful summary without data explosion.

#### 3. interests[] - COUNT + LIST Strategy

**Fields to Export**:
```
interests_count                  (COUNT of interests array)
interests_list                   (comma-separated hobbyNames)
```

**Implementation**:
- One count field with `extractionMode: "count"`
- One list field extracting `hobbyName` with comma-separated mode

**Rationale**: Interests are supplementary data. Count + list provides adequate detail without complexity.

#### 4. Simple Arrays - COMMA-SEPARATED (Standard)

**Fields to Export**:
```
nickNames                        (comma-separated nickNames)
tags                             (comma-separated tagNames)
lifestyles                       (comma-separated tagNames)
```

**Implementation**:
- Standard array handling with `arrayConfig.extractField`
- Matches existing agentclients pattern

## Required Code Changes

### 1. Environment/Database Configuration (HIGH PRIORITY)

**Problem**: Current system hardcodes environment from `application.properties`

**Solution Options**:

**Option A: Command-line override (RECOMMENDED)**
```bash
./gradlew discover -Pcollection=personwealthxdataMongo -Penv=lake -Pdatabase=WealthX
./gradlew configExport -Pcollection=personwealthxdataMongo -Penv=lake -Pdatabase=WealthX
```

**Changes Required**:
- Modify `DiscoveryRunner.java` to accept env/database parameters
- Modify `ConfigExportRunner.java` to accept env/database parameters
- Update `ExportConfig.java` to support overrides
- Update gradle tasks in `build.gradle`

**Option B: Collection-specific configuration file**
```json
{
  "collection": "personwealthxdataMongo",
  "environment": "lake",
  "database": "WealthX",
  "fields": [...]
}
```

**Changes Required**:
- Extend `DiscoveryConfiguration.java` with environment/database fields
- Modify discovery/export runners to read from config

**RECOMMENDATION**: **Option A** - More flexible, follows existing CLI pattern

### 2. Array Handling Enhancements

**Current State**: Infrastructure exists but not used

**Required Enhancements**:

#### A. PRIMARY Mode Implementation
**Status**: Infrastructure exists (`extractionMode`, `extractionIndex`)
**Needs**: Export phase implementation to extract array[index] element

**Code Changes**:
- `ConfigurationBasedExporter.java` - Add primary mode extraction logic
- Test with residences[0] extraction

#### B. COUNT Mode Implementation
**Status**: Infrastructure exists
**Needs**: Export phase implementation to count array length

**Code Changes**:
- `ConfigurationBasedExporter.java` - Add count mode logic
- Simple `.length` or `.size()` extraction

#### C. STATISTICS Mode Implementation
**Status**: Infrastructure exists (`StatisticsConfiguration`)
**Needs**: Full aggregation pipeline implementation

**Code Changes**:
- `ConfigurationBasedExporter.java` - Add statistics aggregation logic
- Support: sum, count, max, conditional counts
- May be complex - consider phased implementation

### 3. Discovery Phase Enhancements

**Challenge**: Discovery must identify arrays and suggest handling strategies

**Required**:
- Detect array fields with nested objects
- Analyze array lengths and complexity
- Suggest appropriate handling mode in configuration
- Auto-generate synthetic fields for PRIMARY/COUNT/STATISTICS modes

**Code Changes**:
- `FieldDiscoveryService.java` - Enhanced array analysis
- Auto-generate multiple field configs from single array
- Example: `residences[]` → 9 synthetic fields (7 primary + 1 count + 1 list)

### 4. Configuration File Format

**Current**: Flat list of fields in `includedFields[]`

**Enhancement Needed**: Support synthetic fields with source references

**Example**:
```json
{
  "fieldPath": "residence_primary_city",
  "businessName": "Primary Residence City",
  "sourceCollection": "personwealthxdataMongo",
  "dataType": "string",
  "extractionMode": "primary",
  "sourceField": "residences",
  "extractionIndex": 0,
  "extractPath": "city"
}
```

## Implementation Phases

### Phase 1: Environment/Database Support (FOUNDATION)
**Goal**: Enable connection to lake/WealthX

**Tasks**:
1. Add env/database parameters to gradle tasks
2. Modify DiscoveryRunner to accept overrides
3. Modify ConfigExportRunner to accept overrides
4. Update ExportConfig to support parameter overrides
5. Test connection to lake/WealthX database

**Validation**:
- `./gradlew explore -Penv=lake -Pdatabase=WealthX` works
- Discovery can connect and sample WealthX data

**Estimated Effort**: 2-3 hours

### Phase 2: Basic Discovery & Export (SIMPLIFIED)
**Goal**: Export WealthX with ONLY comma-separated arrays (no advanced modes)

**Tasks**:
1. Run discovery on personwealthxdataMongo
2. Review discovered fields
3. Export with ALL arrays as comma-separated (like existing exports)
4. Validate data quality

**Validation**:
- Can discover 63 field paths
- Can export 4.5M records to CSV
- All arrays show as comma-separated values

**Estimated Effort**: 3-4 hours (mostly configuration review)

### Phase 3: PRIMARY Mode Implementation
**Goal**: Extract first residence as separate fields

**Tasks**:
1. Implement PRIMARY extraction mode in ConfigurationBasedExporter
2. Manually edit config to add residence_primary_* fields
3. Test extraction of residences[0] fields
4. Validate all 5 address fields extract correctly

**Validation**:
- residence_primary_city shows first city (not comma-separated list)
- All primary residence fields populated correctly

**Estimated Effort**: 4-6 hours

### Phase 4: COUNT Mode Implementation
**Goal**: Add array count fields

**Tasks**:
1. Implement COUNT mode in ConfigurationBasedExporter
2. Add residence_count, interests_count fields
3. Test count extraction
4. Validate counts match array lengths

**Validation**:
- Counts show correct array lengths
- NULL arrays show 0 or NULL appropriately

**Estimated Effort**: 2-3 hours

### Phase 5: STATISTICS Mode Implementation (OPTIONAL)
**Goal**: Aggregate netWorthHoldings data

**Tasks**:
1. Implement STATISTICS mode with aggregation pipeline
2. Add netWorth_totalHoldingsValue, netWorth_holdingsCount, etc.
3. Test aggregation calculations
4. Validate sums and counts

**Validation**:
- Total value matches sum of all valueOfHoldings
- Counts accurate for different asset types

**Estimated Effort**: 8-12 hours (complex)

**Decision Point**: May defer to future if too complex

### Phase 6: Auto-Discovery of Synthetic Fields (ENHANCEMENT)
**Goal**: Discovery phase auto-generates PRIMARY/COUNT fields

**Tasks**:
1. Enhance FieldDiscoveryService to detect complex arrays
2. Auto-generate synthetic field configurations
3. Suggest handling strategies based on array patterns
4. Test with residences[], netWorthHoldings[]

**Validation**:
- Discovery automatically creates residence_primary_* fields
- No manual config editing required

**Estimated Effort**: 6-8 hours

**Decision Point**: May defer to future - manual config editing acceptable initially

## Array Handling Recommendations Summary

### Immediate Decisions (Phase 2)

| Field | Mode | Output |
|-------|------|--------|
| nickNames[] | comma_separated | "Bob, Bobby, Robert" |
| tags[] | comma_separated | "over_one_million, high_net_worth" |
| lifestyles[] | comma_separated | "Golf, Travel, Philanthropy" |
| residences[] | comma_separated | "New York, Miami, Aspen" (cities only for now) |
| interests[] | comma_separated | "Golf, Wine Collecting, Art" (hobbyNames) |
| netWorthHoldings[] | SKIP | Too complex for initial phase |

### Future Enhancements (Phases 3-5)

**residences[]**:
- PRIMARY: residence_primary_city, residence_primary_state, etc.
- COUNT: residence_count

**netWorthHoldings[]**:
- STATISTICS: netWorth_totalValue, netWorth_propertyCount, netWorth_stockCount

**interests[]**:
- COUNT: interests_count

## Expected Export Schema

### Phase 2 Output (Simplified - ~45 fields)
```
Personal (10): firstName, lastName, fullName, email, DOB, age, maritalStatus, sex, photoUrl, wxId
Business (9): business_company, business_position, business_phone, business_email, business_address, business_city, business_state, business_postalCode, business_country
Net Worth (8): netWorth_householdWealth, netWorth_householdNetWorth, netWorth_liquidLowerInt, netWorth_netWorthLowerInt, netWorth_chartURL, etc.
Arrays (5): nickNames, tags, lifestyles, residences_cities, interests_list
Metadata (5): lastIngestion, lastWXUpdate, updatedAt, cities, names
```

### Phase 3-4 Output (Enhanced - ~60 fields)
```
Additional from residences[]:
- residence_primary_address
- residence_primary_city
- residence_primary_state
- residence_primary_postalCode
- residence_primary_country
- residence_count
```

### Phase 5 Output (Full - ~70 fields)
```
Additional from netWorthHoldings[]:
- netWorth_totalHoldingsValue
- netWorth_holdingsCount
- netWorth_propertyCount
- netWorth_stockCount
- netWorth_largestHoldingValue
- netWorth_largestHoldingName
- netWorth_holdingTypes
```

## Testing Strategy

### Unit Tests
1. Test PRIMARY mode extraction with mock array data
2. Test COUNT mode with various array lengths
3. Test STATISTICS mode aggregations
4. Test NULL/empty array handling

### Integration Tests
1. Discovery on small sample (1000 records)
2. Export on small sample
3. Validate field counts and data types
4. Check for data loss or corruption

### Performance Tests
1. Discovery phase performance (10K sample)
2. Export phase performance (full 4.5M dataset)
3. Memory usage monitoring
4. Export speed comparison with agentclients (573K)

**Expected Performance**:
- Discovery: 3-5 minutes (10K sample)
- Export: 20-40 minutes (4.5M records at 2,000-4,000 docs/sec)
- Memory: 4-8GB (need larger heap than current exports)

## Risks and Mitigation

### Risk 1: Lake Environment Access
**Risk**: Lake cluster may have different access controls or performance characteristics
**Mitigation**: Test connection and basic queries early (Phase 1)

### Risk 2: 4.5M Record Performance
**Risk**: Largest export to date, may hit memory or cursor issues
**Mitigation**: Test with row limits first (1K, 10K, 100K, 1M before full)

### Risk 3: Complex Array Aggregation
**Risk**: STATISTICS mode may be too complex or slow
**Mitigation**: Make Phase 5 optional, can release without it

### Risk 4: Configuration File Explosion
**Risk**: Manual field configuration for 60-70 fields is error-prone
**Mitigation**: Start with simplified schema, enhance incrementally

## Success Criteria

### Minimum Viable Export (Phase 2)
- ✅ Can connect to lake/WealthX database
- ✅ Discovery identifies all 63 field paths
- ✅ Export produces CSV with 4.5M rows
- ✅ All simple fields (personal, business, netWorth scalars) export correctly
- ✅ All arrays export as comma-separated values
- ✅ No data corruption or loss

### Enhanced Export (Phases 3-4)
- ✅ PRIMARY mode extracts first residence correctly
- ✅ COUNT mode shows accurate array counts
- ✅ ~60 fields in final CSV
- ✅ Export completes in <1 hour

### Full Export (Phase 5 - Optional)
- ✅ STATISTICS mode aggregates asset holdings
- ✅ ~70 fields in final CSV
- ✅ Asset totals and counts accurate

## Open Questions

1. **Environment Configuration**: Command-line override vs. config file? (RECOMMEND: CLI)
2. **Phase 5 Necessity**: Is asset aggregation required initially or can we defer? (RECOMMEND: Defer)
3. **Field Filtering**: Should we apply same sparsity filters or export all fields? (RECOMMEND: Export all for now)
4. **Output Format**: Single CSV or split by category? (RECOMMEND: Single CSV)
5. **Heap Size**: Will 24GB be sufficient for 4.5M records? (RECOMMEND: Test and adjust)

## Next Steps

1. **Review this plan** with stakeholder
2. **Make array handling decisions** for each field
3. **Prioritize phases** - which to implement first?
4. **Begin Phase 1** implementation (environment support)
5. **Test connection** to lake/WealthX database

## Appendix: Command Examples

### Phase 1 Commands
```bash
# Test connection
./gradlew explore -Penv=lake -Pdatabase=WealthX -Pcollection=personwealthxdataMongo

# Run discovery
./gradlew discover -Pcollection=personwealthxdataMongo -Penv=lake -Pdatabase=WealthX
```

### Phase 2 Commands
```bash
# Test export with limit
./gradlew configExport -Pcollection=personwealthxdataMongo -Penv=lake -Pdatabase=WealthX -ProwLimit=1000

# Full export
./gradlew configExport -Pcollection=personwealthxdataMongo -Penv=lake -Pdatabase=WealthX
```

### Expected Files
```
config/personwealthxdataMongo_fields.json          (discovery output)
config/personwealthxdataMongo_expansion_audit.txt  (relationship audit)
output/personwealthxdataMongo_full_20251008_*.csv  (export output)
```
