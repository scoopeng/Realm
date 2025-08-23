# Discovery Phase Improvements - August 23, 2025

## Problem Identified
The discovery phase was excluding valuable fields (addresses, phone numbers) due to a flawed compound sparsity calculation that was misleading the filtering algorithm.

## Root Cause
The `calculateCompoundSparsityForExpandedFields` method was:
1. Randomly sampling 5,000 documents from referenced collections (e.g., people_meta)
2. Calculating field presence based on these random samples
3. Multiplying parent presence × random sample presence = compound sparsity
4. This led to incorrect exclusions because random people_meta documents weren't necessarily the ones referenced by agentclients

### Example:
- `client_expanded.address.state` appeared in 37% of actual expanded agentclients documents
- But only appeared in <5% of random people_meta samples
- Compound sparsity fell below 5% threshold → field excluded (incorrectly)

## Solution Implemented

### 1. Removed Random Sampling
- Deleted `sampleReferencedCollection()` method
- Deleted `countFieldsInDocument()` helper method  
- Deleted `ParentFieldStats` class
- These were performing unnecessary random sampling when we already had full cached data

### 2. Use Actual Expansion Statistics
- Replaced `compoundSparsity` with `expandedFieldSparsity`
- New `calculateExpandedFieldSparsity()` method uses actual occurrence counts from expansion
- Sparsity now calculated as: `actualOccurrences / documentsScanned`
- This gives TRUE field presence based on actual expanded documents

### 3. Full Cache Utilization
The discovery phase already caches entire collections in memory:
- lifestyles: 14 documents
- tags: 600 documents
- agents: 28,387 documents
- people_meta: 552,466 documents (full cache!)
- teams: 209 documents

With full caching, random sampling was redundant and misleading.

## Results

### Before Fix
- **37 fields included**, 175 excluded (51 marked as sparse)
- Missing critical fields like address.city, address.state, postalCode, primaryPhone

### After Fix  
- **67 fields included**, 145 excluded (only 21 marked as sparse)
- Now includes all address components, phone numbers, and other valuable business data

### Specific Fields Now Included:
- `client_expanded.address.city` (954 distinct values)
- `client_expanded.address.state` (177 distinct values)
- `client_expanded.address.postalCode` (1,278 distinct values)
- `client_expanded.address.streetAddress` (2,804 distinct values)
- `client_expanded.primaryPhone` (4,598 distinct values)
- All other address components (housenumber, streetname, unit, etc.)
- Name components (middleName, prefix, suffix)
- Demographics.birthDate

## Code Changes

### FieldDiscoveryService.java
1. Line 78: Changed `Double compoundSparsity` to `Double expandedFieldSparsity`
2. Line 807: Replaced `calculateCompoundSparsityForExpandedFields()` with `calculateExpandedFieldSparsity()`
3. Lines 891-985: Completely rewrote the calculation method to use actual statistics
4. Lines 990-1082: Removed dead code (sampleReferencedCollection, countFieldsInDocument, ParentFieldStats)
5. Lines 1568-1592: Updated filtering logic to use `expandedFieldSparsity` instead of `compoundSparsity`

### RelationExpander.java
1. Line 106: Changed log level from INFO to DEBUG for cache setting (was flooding logs during parallel processing)

## Key Lessons Learned

1. **Use actual data, not samples**: When you have full cached data, use it instead of random sampling
2. **Context matters**: Random samples from a large collection don't represent the subset actually referenced
3. **Compound calculations can be misleading**: Parent presence × global field presence ≠ actual expanded field presence
4. **Trust the cache**: With 552K documents cached, we have complete, accurate statistics

## Remaining Considerations

### Statistics Mode
- StatisticsFieldGenerator is implemented and working
- Identifies transaction-like collections and generates sum/avg/min/max fields
- Currently no direct relationship from agentclients to transactions
- Would need to add reverse relationships in RelationExpander to enable transaction statistics for clients

### Performance
- Discovery now takes ~3 minutes with full people_meta cache
- Parallel processing using 8 threads achieves ~125 docs/sec
- Memory usage is acceptable even with 552K cached documents

## Next Steps
- Review ConfigurationBasedExporter for similar issues
- Ensure export phase uses the same caching strategy
- Check for any compound sparsity calculations in export
- Verify export phase handles the expanded 67 fields efficiently