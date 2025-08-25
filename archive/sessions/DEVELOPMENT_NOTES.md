# DEVELOPMENT_NOTES.md

Technical decisions, implementation notes, and guidance for future development.

## Key Technical Decisions

### 1. Why Java 11?
- Modern enough for current development practices
- Wide compatibility with enterprise environments
- Good balance between features and stability
- LTS (Long Term Support) version

### 2. Why These Libraries?

**MongoDB Driver Sync (5.2.1)**
- Chose sync over async/reactive for simplicity
- Most CSV exports are batch operations, not real-time
- Easier to understand and maintain

**OpenCSV (5.9)**
- Mature library with proper quote/escape handling
- Handles edge cases (newlines, special characters)
- Good performance for large files
- Simple API

**Typesafe Config (1.4.3)**
- Allows hierarchical configuration
- Environment variable override support
- Type-safe property access
- Good for multi-environment setup

**Logback (1.4.14)**
- De facto standard for Java logging
- Good performance
- Flexible configuration
- SLF4J compatibility

### 3. Architecture Decisions

**Single Responsibility**
- Each class has one clear purpose
- ExportConfig: Configuration only
- MongoToCSVExporter: Export logic only
- Main: Orchestration only

**Strategy Pattern**
- Enum-based strategy selection
- Easy to add new strategies
- Clear separation of export logic

**Resource Management**
- Try-with-resources everywhere
- Proper cleanup of MongoDB connections
- No resource leaks

## Code Patterns and Conventions

### Error Handling
```java
try {
    // operation
} catch (SpecificException e) {
    logger.error("Descriptive message", e);
    throw new RuntimeException("User-friendly message", e);
}
```

### Logging Patterns
- INFO: Major operations (start, complete, progress)
- DEBUG: Detailed operations (not currently used but available)
- WARN: Potential issues (placeholder credentials)
- ERROR: Failures with full stack traces

### Null Handling
- Multi-value fields: null → empty list → single row with empty value
- Regular fields: null → empty string in CSV
- No NullPointerExceptions in normal operation

## Performance Considerations

### Memory Usage
- Cursor-based iteration (no loading full collection)
- Streaming writes to CSV
- Minimal object creation in loops

### Current Performance Characteristics
- Single-threaded processing
- Sequential collection exports
- Progress logging every 1000 documents
- No batching (processes one document at a time)

### Potential Optimizations
1. **Parallel Processing**
   ```java
   // Could process multiple collections in parallel
   ExecutorService executor = Executors.newFixedThreadPool(3);
   futures.add(executor.submit(() -> exportCollection(...)));
   ```

2. **Batch Processing**
   ```java
   // Could fetch documents in batches
   collection.find().batchSize(1000)
   ```

3. **Writer Buffering**
   ```java
   // Already uses BufferedWriter internally via FileWriter
   ```

## Testing Approach

### Current Testing
- `TestMain.java` for manual testing without MongoDB
- Demonstrates both strategies
- Visual verification of output

### Recommended Test Additions
1. **Unit Tests**
   - Test combination generation logic
   - Test sorting behavior
   - Test null handling

2. **Integration Tests**
   - Test with embedded MongoDB
   - Test large datasets
   - Test error scenarios

3. **Performance Tests**
   - Measure export speed
   - Memory usage profiling
   - Large dataset handling

## Common Issues and Solutions

### Issue: MongoDB Connection Timeout
**Symptom**: `MongoTimeoutException`
**Solutions**:
1. Check network connectivity
2. Verify credentials
3. Check MongoDB host accessibility
4. Add connection timeout configuration

### Issue: Out of Memory
**Symptom**: `OutOfMemoryError`
**Solutions**:
1. Increase JVM heap: `-Xmx2g`
2. Ensure cursor-based processing
3. Check for memory leaks

### Issue: CSV Formatting Problems
**Symptom**: Broken CSV in Excel
**Solutions**:
1. OpenCSV handles escaping
2. Check for BOM issues
3. Verify UTF-8 encoding

## Development Workflow

### Adding New Features
1. Update CLAUDE.md with new capability
2. Follow existing patterns
3. Add logging for visibility
4. Update Main.java with examples
5. Test with TestMain.java first

### Making Changes
1. Read existing code first
2. Follow established patterns
3. Maintain backward compatibility
4. Update documentation
5. Test both strategies

### Code Style
- Opening braces on new line (per existing code)
- Meaningful variable names
- Comments only where necessary
- Log important operations

## Security Considerations

### Current State
- Credentials in properties file (not ideal)
- No encryption of output files
- No access control on exports

### Production Recommendations
1. Use environment variables for credentials
2. Implement proper secret management
3. Add authentication to export service
4. Consider encrypting sensitive exports
5. Audit trail for exports

## Debugging Tips

### Enable Debug Logging
Add to logback.xml:
```xml
<logger name="com.example.mongoexport" level="DEBUG"/>
```

### MongoDB Connection Issues
Test connection with MongoDB shell:
```bash
mongosh "mongodb://user:pass@host:27017/?authSource=admin"
```

### CSV Output Issues
- Check ./output directory permissions
- Verify disk space
- Look for special characters in data

## Future Architecture Considerations

### Microservice Architecture
Could split into:
- Configuration service
- Export service
- File management service

### Cloud Deployment
Consider:
- Containerization (Docker)
- Kubernetes deployment
- Cloud storage for outputs
- Managed MongoDB connections

### Scalability
- Horizontal scaling with job queue
- Distributed processing with Spark
- Cloud-native solutions

## Maintenance Tasks

### Regular Updates
1. Update dependencies quarterly
2. Review security advisories
3. Test with new MongoDB versions
4. Update documentation

### Monitoring
Consider adding:
- Export job duration metrics
- Success/failure rates
- Data volume metrics
- Resource usage tracking