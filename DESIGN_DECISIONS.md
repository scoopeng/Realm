# Design Decisions and Architecture

This document captures the key architectural decisions and design choices for the Realm MongoDB Real Estate Data Exporter.

## Current Architecture (2025-08-06)

### Core System Design

```
┌─────────────────────────────────────────┐
│     Comprehensive Export System          │
│  (ComprehensiveExporter - Main Entry)    │
├─────────────────────────────────────────┤
│     Enhanced Exporters Layer            │
│  (EnhancedListingsExporter, etc.)       │
├─────────────────────────────────────────┤
│     Core Components Layer               │
│  (RelationExpander, FieldStatistics)    │
├─────────────────────────────────────────┤
│     Base Infrastructure Layer           │
│  (AbstractUltraExporter, ExportConfig)  │
├─────────────────────────────────────────┤
│     Data Access Layer                   │
│  (MongoDB Driver, CSV Writer)           │
└─────────────────────────────────────────┘
```

## Key Design Decisions

### 1. Automatic Relation Expansion

**Decision**: Automatically traverse and expand foreign key relationships during export.

**Implementation**:
- `RelationExpander` class with predefined relationship mappings
- Configurable depth (default: 2 levels) to prevent infinite recursion
- Circular reference detection
- Intelligent caching for frequently accessed collections

**Rationale**:
- MongoDB's normalized structure requires joining multiple collections
- Manual joins are error-prone and incomplete
- Automatic expansion ensures complete data capture

**Benefits**:
- Complete data export without missing relationships
- No manual configuration required
- Discovers all fields across related objects

### 2. Real-Time Field Statistics Collection

**Decision**: Collect comprehensive field statistics during export operations.

**Implementation**:
- `FieldStatisticsCollector` tracks every field encountered
- Categorizes as: ALWAYS_EMPTY, SINGLE_VALUE, SPARSE, or MEANINGFUL
- Generates JSON summaries for future filtering
- Thread-safe for future parallelization

**Rationale**:
- MongoDB documents often have 30-50% sparse or empty fields
- Data-driven filtering more accurate than static configuration
- Enables intelligent, automatic optimization

### 3. Multiple Export Modes

**Decision**: Provide four distinct modes for different use cases.

**Modes**:
```
Full Mode     - Complete export with expansion and statistics
Analyze Mode  - Statistics only, no data export
Filtered Mode - Uses saved summaries to exclude sparse fields
Minimal Mode  - Basic export without enhancements
```

**Rationale**:
- Different use cases require different levels of detail
- Separation of analysis from export improves workflow
- Flexibility without complexity

### 4. Base Class Architecture

**Decision**: Use `AbstractUltraExporter` as foundation for all exporters.

**Implementation**:
- Template method pattern for export workflow
- Abstract methods for collection-specific logic
- Shared statistics and metadata generation
- Common CSV writing and progress tracking

**Benefits**:
- Eliminates code duplication
- Ensures consistent behavior
- Simplifies maintenance and extensions

### 5. In-Memory Collection Caching

**Decision**: Preload small-to-medium collections entirely into memory.

**Strategy**:
```java
Collections < 100K documents  → Fully cached in memory
Collections 100K-1M documents → Selective caching
Collections > 1M documents    → On-demand queries only
```

**Performance Impact**:
- 10-100x faster foreign key lookups
- Eliminates N+1 query problem
- Trade-off: 20GB heap requirement

### 6. Dynamic Field Discovery

**Decision**: Discover fields from actual data, not predefined schemas.

**Implementation**:
- Scan real documents during export
- Build headers dynamically
- Track statistics for every discovered field
- No schema maintenance required

**Rationale**:
- MongoDB is schemaless - documents vary
- Static schemas miss optional fields
- Real data provides ground truth

### 7. Consolidated Gradle Tasks

**Decision**: Simplify from 9+ tasks to 4 primary tasks.

**New Structure**:
```gradle
comprehensiveExport  # Main with mode selection
analyzeFields        # Analysis only
filteredExport       # Using saved summaries
export*              # Standard per-collection
```

**Benefits**:
- Cleaner, more intuitive interface
- Less maintenance overhead
- Parameters provide fine control

## Performance Characteristics

### Current Performance

| Operation | Records/Second | Memory Usage | Notes |
|-----------|---------------|--------------|-------|
| Listings Export | 3,500 | 20GB | With property expansion |
| Agents Export | 20,000 | 15GB | Smaller documents |
| Transactions Export | 5,600 | 18GB | Complex relationships |
| Field Analysis | 10,000 | 5GB | Statistics only |

### Optimization Strategies

1. **Batch Processing**: 2,000 record batches prevent memory overflow
2. **HashMap Lookups**: O(1) access for cached collections
3. **Lazy Expansion**: Only expand when needed
4. **Selective Caching**: Based on collection size

## Trade-offs and Decisions

### Accepted Trade-offs

| Choice | Benefit | Cost |
|--------|---------|------|
| 20GB Heap | 10-100x performance | High memory requirement |
| Full Caching | Fast lookups | Longer startup time |
| Automatic Expansion | Complete data | Larger output files |
| Multiple Modes | Flexibility | Code complexity |

### Rejected Alternatives

1. **Streaming Exports**: Would be slower, chose batch processing
2. **Generic Exporter**: Less performant than specialized exporters
3. **Schema Files**: Would require maintenance, chose dynamic discovery
4. **Parallel Processing**: Added complexity, current speed sufficient

## Future Enhancements

### Planned Improvements

1. **Complete Enhanced Exporters**: Full implementation for transactions and agents
2. **Parallel Batch Processing**: Multi-threaded export for scale
3. **Incremental Exports**: Only export changed records
4. **Custom Field Mappings**: User-defined transformations

### Potential Features

- Streaming mode for unlimited scale
- Multiple output formats (Parquet, JSON)
- Real-time change detection
- GraphQL API for queries

## Security Considerations

### Current Measures

- Read-only MongoDB operations
- Credentials in external config files
- No data modification capabilities
- Input validation for collection names

### Best Practices

- Never log sensitive data
- Use connection pooling
- Bounded resource consumption
- Safe string handling

## Architecture Principles

### Core Principles

1. **Performance First**: Optimize for speed over flexibility
2. **Data Completeness**: Include all related data by default
3. **Intelligent Defaults**: Smart behavior out of the box
4. **Progressive Enhancement**: Basic features always work
5. **Clean Separation**: Clear boundaries between components

### Design Patterns Used

- **Template Method**: Export workflow in AbstractUltraExporter
- **Builder**: ExportOptions configuration
- **Strategy**: Export modes and behaviors
- **Factory**: Exporter creation based on collection
- **Visitor**: Field scanning and statistics

## Conclusion

The current architecture successfully balances performance, flexibility, and maintainability. The automatic relation expansion and field statistics collection provide intelligent, data-driven exports without manual configuration. The modular design allows for incremental improvements while maintaining backward compatibility.