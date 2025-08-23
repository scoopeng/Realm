# Array Modes Schema Design Document
## Configuration Schema for Enhanced Array Handling

### Overview
This document defines the JSON schema design for three array display modes in the MongoDB export system. The design prioritizes practical utility for real estate data extraction while maintaining backward compatibility.

---

## Core Design Principles

1. **Backward Compatible**: Existing configurations must continue to work
2. **Self-Documenting**: Configuration should be clear without external documentation
3. **Minimal Complexity**: Avoid nested complexity where possible
4. **Value-Focused**: Every field should provide clear value for AI chat consumption

---

## Display Modes

### 1. LIST Mode (Current Default)
Extracts a single field from array elements and joins them with a separator.

**Use Cases:**
- List of lifestyle preferences
- List of tag names
- Simple string arrays

**Configuration Structure:**
```json
{
  "fieldPath": "realmData.lifestyles",
  "businessName": "Lifestyle Preferences",
  "dataType": "array",
  "include": true,
  "arrayConfig": {
    "displayMode": "list",  // Optional, this is default
    "objectType": "object",
    "referenceCollection": "lifestyles",
    "extractField": "lifestyleName",
    "separator": ", "  // Default: ", "
  }
}
```

**Output Example:**
```
"Golf, Tennis, Beach Living, Mountain Views"
```

---

### 2. PRIMARY Mode (New)
Extracts multiple fields from the first element of the array, with optional count.

**Use Cases:**
- Primary agent details from agent team
- First email/phone from contact arrays
- Main address from multiple addresses

**Configuration Structure:**
```json
{
  "fieldPath": "agents",
  "businessName": "Agent Team",
  "dataType": "array",
  "include": true,
  "arrayConfig": {
    "displayMode": "primary",
    "objectType": "objectId",
    "referenceCollection": "agents",
    "primaryFields": [
      {
        "field": "fullName",
        "businessName": "Primary Agent Name"
      },
      {
        "field": "primaryEmail",
        "businessName": "Primary Agent Email"
      },
      {
        "field": "primaryPhone",
        "businessName": "Primary Agent Phone"
      },
      {
        "field": "brokerages[0].name",
        "businessName": "Primary Agent Brokerage"
      }
    ],
    "includeCount": true,
    "countBusinessName": "Total Agent Count"
  }
}
```

**Generated Fields in Discovery:**
The discovery process will generate separate field configurations for each primary field:

```json
[
  {
    "fieldPath": "agents",
    "businessName": "Agent Team",
    "dataType": "array",
    "include": false,  // Original array field disabled
    "arrayConfig": { "displayMode": "list", "extractField": "fullName" }
  },
  {
    "fieldPath": "agents[primary].fullName",
    "businessName": "Primary Agent Name",
    "sourceField": "agents",
    "dataType": "string",
    "include": true,
    "extractionMode": "primary",
    "extractionIndex": 0
  },
  {
    "fieldPath": "agents[primary].primaryEmail",
    "businessName": "Primary Agent Email",
    "sourceField": "agents",
    "dataType": "string",
    "include": true,
    "extractionMode": "primary",
    "extractionIndex": 0
  },
  {
    "fieldPath": "agents[count]",
    "businessName": "Total Agent Count",
    "sourceField": "agents",
    "dataType": "integer",
    "include": true,
    "extractionMode": "count"
  }
]
```

**Output Example:**
```
Primary Agent Name: "John Doe"
Primary Agent Email: "john@realty.com"
Primary Agent Phone: "555-0100"
Primary Agent Brokerage: "Acme Realty"
Total Agent Count: 4
```

---

### 3. STATISTICS Mode (New)
Generates aggregate statistics for arrays of related objects (especially transactions).

**Use Cases:**
- Transaction summaries (count, total, average)
- Property portfolio statistics
- Activity metrics

**Configuration Structure:**
```json
{
  "fieldPath": "transactions",
  "businessName": "Transaction History",
  "dataType": "array",
  "include": false,  // Original array typically excluded
  "arrayConfig": {
    "displayMode": "statistics",
    "objectType": "objectId",
    "referenceCollection": "transactions",
    "statisticsFields": [
      {
        "operation": "count",
        "businessName": "Total Transactions"
      },
      {
        "operation": "sum",
        "field": "saleAmount",
        "businessName": "Total Sales Volume"
      },
      {
        "operation": "avg",
        "field": "saleAmount",
        "businessName": "Average Sale Price"
      },
      {
        "operation": "max",
        "field": "saleAmount",
        "businessName": "Highest Sale"
      },
      {
        "operation": "min",
        "field": "closedDate",
        "businessName": "First Transaction Date"
      },
      {
        "operation": "max",
        "field": "closedDate",
        "businessName": "Latest Transaction Date"
      }
    ]
  }
}
```

**Generated Fields in Discovery:**
Similar to primary mode, each statistic becomes a separate field:

```json
[
  {
    "fieldPath": "transactions[stats].count",
    "businessName": "Total Transactions",
    "sourceField": "transactions",
    "dataType": "integer",
    "include": true,
    "extractionMode": "statistics",
    "statisticsOperation": "count"
  },
  {
    "fieldPath": "transactions[stats].totalSales",
    "businessName": "Total Sales Volume",
    "sourceField": "transactions",
    "dataType": "double",
    "include": true,
    "extractionMode": "statistics",
    "statisticsOperation": "sum",
    "statisticsField": "saleAmount"
  }
]
```

**Output Example:**
```
Total Transactions: 12
Total Sales Volume: 4500000
Average Sale Price: 375000
Highest Sale: 750000
First Transaction Date: "2020-03-15"
Latest Transaction Date: "2024-11-28"
```

---

## Backward Compatibility Strategy

### Existing Configurations
- All existing configurations implicitly use `displayMode: "list"`
- If `displayMode` is not specified, default to "list"
- Existing field structures remain unchanged

### Migration Path
1. Discovery phase will generate BOTH old-style and new-style fields
2. Old-style fields marked as `include: false` when better alternatives exist
3. Users can enable/disable fields as needed
4. No breaking changes to existing exports

---

## Implementation Approach (Small Extension of Current Behavior)

### Core Insight
This is a **small extension** of the current discovery behavior. Instead of generating ONE field configuration per array, we generate MULTIPLE field configurations - one for each useful field we can extract.

### Phase 1: Discovery Enhancement
1. Detect array fields during discovery (current behavior)
2. For each array field, generate MULTIPLE field configurations:
   - Original list mode field (current behavior, kept for compatibility)
   - Additional "virtual" fields for primary mode (new):
     - One config for each interesting field in the first element
     - One config for the count
   - Additional "virtual" fields for statistics mode (new):
     - One config for each statistic operation
3. Each generated field has a special naming pattern (`[primary]`, `[stats]`, `[count]`)
4. Mark appropriate fields as `include: true/false` based on value

**Example**: The `agents` array field generates:
- `agents` → comma-separated list (existing)
- `agents[primary].fullName` → first agent's name (new)
- `agents[primary].primaryEmail` → first agent's email (new)
- `agents[count]` → total count (new)

### Phase 2: Export Enhancement
1. ConfigurationBasedExporter detects special patterns in field path
2. Routes to appropriate extraction logic:
   - Path contains `[primary]` → Extract from first array element
   - Path contains `[count]` → Return array length
   - Path contains `[stats]` → Run aggregation pipeline
   - Otherwise → Current comma-separated list behavior

---

## Field Naming Conventions

### Primary Mode Fields
- Pattern: `{arrayField}[primary].{extractedField}`
- Example: `agents[primary].email`
- Business Name: Use provided or auto-generate

### Statistics Fields
- Pattern: `{arrayField}[stats].{operation}_{field}`
- Example: `transactions[stats].sum_saleAmount`
- Business Name: Use provided or auto-generate

### Count Fields
- Pattern: `{arrayField}[count]`
- Example: `agents[count]`
- Business Name: "Total {ArrayName}" or provided

---

## Data Type Mapping

### Primary Mode
- Extracted fields inherit the data type of the source field
- Count fields are always "integer"

### Statistics Mode
- `count` → integer
- `sum`, `avg` → double (for numeric fields)
- `min`, `max` → inherit source field type
- `distinctCount` → integer

---

## Edge Case Handling

### Empty Arrays
- **List Mode**: Return empty string
- **Primary Mode**: Return null for all fields, 0 for count
- **Statistics Mode**: Return 0 for count, null for other operations

### Null/Missing Arrays
- All modes return null

### Arrays with Null Elements
- **List Mode**: Skip null elements
- **Primary Mode**: Skip to first non-null element
- **Statistics Mode**: Exclude nulls from calculations

---

## Performance Considerations

### Caching Strategy
- **Primary Mode**: Cache referenced collections <1M docs
- **Statistics Mode**: Pre-calculate all statistics before export
- **List Mode**: Current caching behavior (unchanged)

### Batch Processing
- Statistics calculated in batches of 1000 documents
- Primary lookups batched where possible
- Maintain current export speed of 1000+ rows/sec

---

## Examples for AgentClients Collection

### Current Problem
```json
{
  "fieldPath": "agents",
  "output": "Emily Eldredge, Graham Faupel, Julie Faupel, Karen Terra, Luna Wang, Mack Mendenhall, Matt Faupel, tess hartnett"
}
```
**Issue**: Too long, no details beyond names

### Solution with Primary Mode
```json
{
  "Primary Agent Name": "Emily Eldredge",
  "Primary Agent Email": "emily@realty.com",
  "Primary Agent Phone": "555-0001",
  "Total Agents": 8
}
```
**Benefit**: Clean, structured, useful for AI chat

### Email Array Problem
```json
{
  "fieldPath": "client_expanded.emails",
  "output": "[Document{{_id=false, address=email@example.com}}]"
}
```

### Solution with Primary Mode
```json
{
  "Client Primary Email": "email@example.com"
}
```
**Benefit**: Clean email string instead of document structure

---

## Success Criteria

1. **Data Quality**: Clean, meaningful fields for AI consumption
2. **Performance**: Maintain 1000+ rows/sec export speed
3. **Compatibility**: Zero breaking changes to existing exports
4. **Simplicity**: Configuration remains understandable
5. **Value**: Each mode provides clear benefit over list mode

---

## Future Considerations (Not in Current Scope)

1. **Smart Primary Selection**: Use criteria beyond "first" (e.g., most recent, primary flag)
2. **Conditional Statistics**: Filter data before aggregation
3. **Custom Extractors**: User-defined extraction logic
4. **Nested Array Support**: Handle arrays within arrays

---

*This design focuses on the 80% of use cases that provide maximum value for real estate data extraction.*