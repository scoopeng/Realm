# Statistics Mode Design Document

## Overview
Statistics mode generates aggregated metrics for arrays that reference transaction-like collections. Instead of showing individual items, it provides business insights through calculated statistics.

## Detection Heuristics

### 1. Transaction-Like Collection Detection
A collection is considered "transaction-like" if it contains:

#### Required Indicators (at least 2):
- Field names containing: "price", "amount", "cost", "value", "total", "sum"
- Field names containing: "date", "time", "timestamp", "created", "closed", "completed"
- Numeric fields with high distinct values (>50% documents have different values)
- Date fields showing transaction timing

#### Strong Indicators:
- Collection name contains: "transaction", "order", "sale", "purchase", "payment", "invoice"
- Fields named: "sale_price", "purchase_price", "order_total", "amount_paid"
- Multiple date fields (created, completed, processed)
- Status/state fields indicating workflow

#### Exclusion Criteria:
- Collections with <100 documents (too small for meaningful statistics)
- Collections with >90% null values in numeric fields
- Reference/lookup collections (categories, types, statuses)

### 2. Numeric Field Detection
Fields suitable for statistics must be:
- Type: Integer, Long, Double, Decimal128
- NOT: String (unless parseable as number)
- NOT: Boolean (use count instead)
- Have >10% non-null values
- Have >10 distinct values (not just 0/1)

### 3. Statistics Generation Rules

#### For Numeric Fields:
```
COUNT - Always generated
SUM - If field represents money/quantity
AVG - If field represents money/quantity/duration
MIN - If meaningful range exists
MAX - If meaningful range exists
MEDIAN - If >100 samples (expensive operation)
STDDEV - If statistical analysis needed
```

#### For Date Fields:
```
MIN - Earliest transaction
MAX - Latest transaction
COUNT_BY_MONTH - Transaction velocity
COUNT_BY_YEAR - Annual trends
```

#### For Arrays:
```
COUNT - Number of items
COUNT_DISTINCT - Unique items
```

## Implementation Strategy

### Phase 1: Detection
1. When discovering an array field pointing to another collection
2. Load sample of 100 documents from target collection
3. Analyze field types and values
4. Apply heuristics to determine if statistics-worthy

### Phase 2: Field Generation
For each qualifying array, generate multiple statistics fields:

```json
{
  "fieldPath": "transactions[stats].total_volume",
  "businessName": "Total Transaction Volume",
  "dataType": "decimal",
  "extractionMode": "statistics",
  "statisticsConfig": {
    "sourceField": "transactions",
    "targetCollection": "transactionsderived",
    "aggregation": "sum",
    "targetField": "sale_price",
    "groupBy": null
  }
}
```

### Phase 3: Export Calculation
Statistics are calculated using MongoDB aggregation pipelines:

```javascript
// Example: Sum of sale_price for client's transactions
db.transactionsderived.aggregate([
  { $match: { client_id: ObjectId("...") } },
  { $group: {
    _id: null,
    total: { $sum: "$sale_price" },
    count: { $sum: 1 },
    avg: { $avg: "$sale_price" }
  }}
])
```

## Field Naming Convention

Statistics fields follow this pattern:
- `{arrayField}[stats].{aggregation}_{targetField}`
- Examples:
  - `transactions[stats].sum_sale_price`
  - `transactions[stats].avg_days_on_market`
  - `transactions[stats].count`
  - `orders[stats].total_revenue`

## Business Names
- Use clear, non-technical names
- Include aggregation type
- Examples:
  - "Total Transaction Volume" (not "Sum of sale_price")
  - "Average Days on Market" (not "Avg days_on_market")
  - "Number of Transactions" (not "Count")

## Performance Considerations

### Caching Strategy
- Statistics are expensive to calculate
- Cache results at document level during export
- Pre-calculate for entire export batch if possible

### Batch Processing
- Group documents by common references
- Calculate statistics once per unique reference
- Use MongoDB $in operator for batch lookups

### Limits
- Max 10 statistics fields per array (configurable)
- Skip statistics if target collection >1M documents
- Timeout aggregations after 10 seconds

## Example Configurations

### 1. Agent Performance Statistics
```json
{
  "fieldPath": "transactions[stats].total_volume",
  "businessName": "Total Sales Volume",
  "extractionMode": "statistics",
  "statisticsConfig": {
    "sourceField": "transactions",
    "targetCollection": "transactionsderived",
    "aggregation": "sum",
    "targetField": "sale_price",
    "matchField": "listingAgents",
    "matchValue": "{document._id}"
  }
}
```

### 2. Client Transaction Statistics
```json
{
  "fieldPath": "transactions[stats].avg_price",
  "businessName": "Average Purchase Price",
  "extractionMode": "statistics",
  "statisticsConfig": {
    "sourceField": "transactions",
    "targetCollection": "transactionsderived",
    "aggregation": "avg",
    "targetField": "sale_price",
    "matchField": "buyers",
    "matchValue": "{document.client}"
  }
}
```

### 3. Time-Based Statistics
```json
{
  "fieldPath": "transactions[stats].last_year_count",
  "businessName": "Transactions Last Year",
  "extractionMode": "statistics",
  "statisticsConfig": {
    "sourceField": "transactions",
    "targetCollection": "transactionsderived",
    "aggregation": "count",
    "dateFilter": {
      "field": "closing_date",
      "range": "last_year"
    }
  }
}
```

## Testing Strategy

1. Start with `agentclients` → `transactionsderived`
2. Generate statistics for agent performance
3. Verify calculations manually
4. Test performance with 100, 1000, 10000 documents
5. Implement caching based on results

## Success Metrics
- Statistics accuracy: 100% match with manual calculation
- Performance: <5 seconds for 1000 documents
- Memory usage: <1GB for full export
- User value: Provides insights not available in raw data