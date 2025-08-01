# MongoDB to CSV Exporter

A production-ready Java application for exporting MongoDB collections to CSV files with flexible multi-value field handling.

## Features

- Export MongoDB collections to CSV format
- Two export strategies for handling multi-value fields:
  - **DENORMALIZED**: Creates one row per multi-value item
  - **DELIMITED**: Creates one row per document with multi-values as comma-separated strings
- Environment-based configuration (dev/stage/prod)
- Robust error handling and logging
- UTF-8 encoding support
- Automatic output directory creation

## Prerequisites

- Java 11 or higher
- MongoDB instance
- Gradle (wrapper included)

## Configuration

1. Copy the example configuration:
   ```bash
   cp src/main/resources/application.properties src/main/resources/application-local.properties
   ```

2. Edit `application-local.properties` and update MongoDB credentials:
   ```properties
   # Replace placeholders with actual values
   mongodb.url.dev=mongodb://username:password@localhost:27017/?authSource=admin
   mongodb.url.stage=mongodb://username:password@stage-host:27017/?authSource=admin
   mongodb.url.prod=mongodb://username:password@prod-host:27017/?authSource=admin
   
   # Set your current environment
   current.environment=dev
   
   # Configure database name
   database.name=your_database_name
   
   # Set export strategy (DENORMALIZED or DELIMITED)
   export.strategy=DENORMALIZED
   ```

## Building and Running

### Build the project
```bash
./gradlew build
```

### Run the exporter
```bash
./gradlew run
```

The application will:
1. Connect to MongoDB using the configured environment settings
2. Export the collections defined in `Main.java`
3. Save CSV files to the `./output` directory

## Export Strategies

### DENORMALIZED Strategy
Creates multiple rows when a document has multi-value fields.

Example input document:
```json
{
  "_id": "123",
  "name": "John Doe",
  "email": "john@example.com",
  "interests": ["sports", "music", "travel"]
}
```

Output CSV:
```csv
_id,name,email,interests
123,John Doe,john@example.com,sports
123,John Doe,john@example.com,music
123,John Doe,john@example.com,travel
```

### DELIMITED Strategy
Creates one row per document with multi-values joined by commas. **Values are sorted alphabetically for consistency**.

Example input document:
```json
{
  "_id": "123",
  "name": "John Doe",
  "email": "john@example.com",
  "interests": ["sports", "music", "travel"]
}
```

Output CSV (interests sorted alphabetically):
```csv
_id,name,email,interests
123,John Doe,john@example.com,"music,sports,travel"
```

This sorting ensures that documents with the same multi-value elements always produce identical CSV output, regardless of the order in MongoDB.

## Customizing Exports

To export different collections or fields, modify the `Main.java` file:

```java
private static void exportCustomCollection(MongoToCSVExporter exporter) {
    List<String> multiValueFields = Arrays.asList("tags", "categories");
    List<String> fieldsToExport = Arrays.asList(
        "_id", 
        "title", 
        "description", 
        "tags", 
        "categories"
    );
    
    exporter.exportCollection("mycollection", multiValueFields, fieldsToExport);
}
```

## Output Files

CSV files are saved to the `./output` directory with the naming convention:
```
{collection}_{strategy}_{timestamp}.csv
```

Example: `clients_denormalized_20241201_143052.csv`

## Logging

The application uses SLF4J with Logback for logging. Logs include:
- Connection status
- Export progress
- Document counts
- Error messages

## Error Handling

The application includes comprehensive error handling for:
- MongoDB connection failures
- Missing collections
- File I/O errors
- Invalid configuration

## Performance Considerations

- Documents are processed in batches with progress logging every 1000 documents
- Uses try-with-resources for proper resource management
- Efficient memory usage with cursor-based iteration

## License

This project is proprietary software.