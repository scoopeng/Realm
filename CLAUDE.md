# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run Commands

- **Build**: `./gradlew build`
- **Run**: `./gradlew run`
- **Clean**: `./gradlew clean`
- **Test**: `./gradlew test`

## Project Architecture

This is a MongoDB to CSV export utility with the following key components:

1. **ExportStrategy**: Enum defining export strategies (DENORMALIZED, DELIMITED)
2. **ExportConfig**: Configuration loader using Typesafe Config, manages environment-specific MongoDB URLs
3. **MongoToCSVExporter**: Core export logic with two strategies:
   - DENORMALIZED: Creates multiple rows for documents with multi-value fields
   - DELIMITED: Creates single rows with comma-separated multi-values
4. **Main**: Entry point demonstrating exports of different collections

## Key Technical Details

- Java 11 target
- Uses MongoDB driver 5.2.1 for database connectivity
- OpenCSV 5.9 for CSV writing
- SLF4J/Logback for logging
- Typesafe Config for configuration management
- UTF-8 encoding throughout

## Configuration

Application uses `application.properties` with environment-based MongoDB URLs. Supports dev/stage/prod environments. Local overrides should use `application-local.properties` (gitignored).

## Export Strategy Logic

- **DENORMALIZED**: Generates all combinations of multi-value fields, creating one CSV row per combination
- **DELIMITED**: Joins multi-value fields with commas, maintaining one row per document

Both strategies handle null/empty values gracefully and support nested field access (e.g., "address.city").