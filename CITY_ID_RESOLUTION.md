# Brokerage City ID Resolution

## Issue Description

The `brokerages` collection in MongoDB contains a numeric `city` field instead of a string city name. This causes the brokerage city to appear as a number in the CSV export instead of the actual city name.

## Investigation

1. **Problem**: The `UltraListingsExporter.java` uses `safeGetString(currentAgentBrokerage, "city")` which simply converts the numeric value to a string, resulting in outputs like "123" instead of "Los Angeles".

2. **Potential Solution**: The `usCities` collection might contain a mapping between numeric IDs and city names. We need to:
   - Check if `usCities` documents have an ID field that corresponds to the numeric city values in brokerages
   - Create a lookup mechanism to resolve numeric city IDs to city names

## Solution Implementation

### 1. TestCityMapping.java
Created a test utility to investigate the structure of both collections and find the mapping pattern.

Run this to investigate the database structure:
```bash
./gradlew run -PmainClass=com.example.mongoexport.TestCityMapping
```

### 2. UltraListingsExporterFixed.java
Created an enhanced version of the exporter with city ID resolution capabilities:

- Added multiple city mapping collections:
  - `usCitiesMap` - Original mapping by city+state
  - `usCitiesById` - New mapping by ObjectId
  - `usCitiesByNumericId` - New mapping by numeric ID

- Added `resolveCityField()` method that:
  - Returns string values as-is
  - Looks up numeric values in the city maps
  - Handles ObjectId references
  - Falls back to string conversion if no mapping found

- Updated brokerage city extraction to use the resolver:
```java
// Handle city field that might be numeric ID
Object cityValue = currentAgentBrokerage.get("city");
String resolvedCity = resolveCityField(cityValue);
row.add(resolvedCity);
```

### Usage

To use the fixed exporter:
```bash
./gradlew run -PmainClass=com.example.mongoexport.UltraListingsExporterFixed
```

## Next Steps

1. Run `TestCityMapping` to understand the actual database structure
2. Based on findings, adjust the city ID mapping logic if needed
3. Consider creating a dedicated city lookup service if the mapping is more complex

## Alternative Approaches

If the numeric city IDs don't map to the `usCities` collection:

1. **Check for a separate mapping collection** - There might be a `cityMappings` or similar collection
2. **Use a different city source** - The city might be available in related documents (like the agent's person record)
3. **External lookup** - The numeric IDs might correspond to an external system (like FIPS codes)
4. **Fallback to state/zip** - Use state and zipcode to infer the city name

## Notes

- The fix preserves backward compatibility - if city is already a string, it works as before
- Logging is added to track successful and failed city resolutions
- Memory usage is slightly increased due to additional city mappings, but should be negligible with 20GB available