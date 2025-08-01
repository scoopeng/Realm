# PROJECT_CONTEXT.md

This document provides detailed context about the business logic and implementation decisions for future development sessions.

## Project Purpose

This MongoDB to CSV exporter was created to solve a specific data extraction challenge: exporting MongoDB collections that contain multi-value (array) fields into CSV format. The challenge is that CSV is inherently a flat format, while MongoDB documents can contain nested arrays.

## Business Requirements

### Core Requirements
1. Export MongoDB collections to CSV format
2. Handle multi-value fields (arrays) in two different ways
3. Support multiple environments (dev/stage/prod)
4. Generate timestamped output files
5. Provide progress tracking for large exports
6. Ensure consistent output (sorting for reproducibility)

### Multi-Value Field Challenge

Consider a MongoDB document:
```json
{
  "_id": "123",
  "name": "John Doe",
  "interests": ["sports", "music", "travel"],
  "skills": ["java", "python"]
}
```

How do you represent this in CSV? Two strategies were implemented:

1. **DENORMALIZED**: Create multiple rows (one per combination)
   - With 3 interests and 2 skills = 6 rows total
   - Each row contains one interest and one skill
   - Useful for pivot tables and analytics

2. **DELIMITED**: Create one row with comma-separated values
   - Single row: `123,John Doe,"music,sports,travel","java,python"`
   - Values are sorted alphabetically for consistency
   - Useful for preserving document structure

## Implementation Details

### Why These Design Choices?

1. **Sorting in DELIMITED mode**: 
   - Ensures identical CSV output for documents with same values in different order
   - Makes exports comparable across runs
   - Facilitates data validation and testing

2. **Combination generation in DENORMALIZED mode**:
   - Uses recursive algorithm to generate all combinations
   - Handles any number of multi-value fields
   - Empty arrays are handled gracefully (single row with empty value)

3. **Configuration-based environment selection**:
   - Allows same codebase to work across dev/stage/prod
   - Credentials stored in properties file (should use secrets management in production)

4. **Cursor-based processing**:
   - Prevents memory issues with large collections
   - Progress logging every 1000 documents for visibility

### Edge Cases Handled

1. **Null/Missing multi-value fields**: Treated as empty, produces single row
2. **Empty arrays**: Produces single row with empty string for that field
3. **Nested field access**: Supports dot notation (e.g., "address.city")
4. **Large collections**: Cursor-based iteration prevents OOM
5. **Special characters in CSV**: Proper escaping via OpenCSV

## Current Implementation Status

### What's Working
- Both export strategies fully implemented
- Sorting ensures consistent output
- Configuration management for multiple environments
- Progress tracking and logging
- Test mode for demonstration without MongoDB

### What Could Be Enhanced
1. **Parallel processing**: Could process multiple collections concurrently
2. **Incremental exports**: Could support date-based incremental exports
3. **Custom delimiters**: Currently hardcoded to comma
4. **Compression**: Could compress output files for large exports
5. **Schema validation**: Could validate fields exist before export

## Usage Patterns

### Typical Workflow
1. Configure MongoDB credentials in application.properties
2. Select export strategy based on downstream requirements
3. Run export for specific collections
4. Output files generated in ./output directory
5. Files are timestamped for easy identification

### When to Use Each Strategy

**Use DENORMALIZED when:**
- Feeding data into analytics tools that expect flat structure
- Need to perform aggregations on multi-value fields
- Creating pivot tables or cross-tabs
- Each multi-value item needs individual processing

**Use DELIMITED when:**
- Preserving document structure is important
- Downstream system can parse delimited values
- Want one row per entity
- Need compact representation

## MongoDB Connection Details

Current configuration points to MongoDB instances with credentials stored in application.properties. In production, consider:
- Using connection strings from environment variables
- Implementing proper secret management
- Adding connection pooling configuration
- Implementing retry logic for transient failures

## Future Considerations

1. **Performance**: Current implementation is sequential. For very large datasets, consider:
   - Parallel collection processing
   - Streaming writes to reduce memory usage
   - Batch processing with checkpoints

2. **Flexibility**: Could add:
   - Custom field transformations
   - Date formatting options
   - Numeric precision control
   - Custom delimiter options

3. **Monitoring**: Could add:
   - Export metrics (rows/second, total time)
   - Failed document tracking
   - Memory usage monitoring

## Testing Strategy

- `TestMain.java` provides standalone testing without MongoDB
- Demonstrates both export strategies
- Shows sorting behavior for DELIMITED mode
- Can be extended for more test cases