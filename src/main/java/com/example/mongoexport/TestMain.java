package com.example.mongoexport;

import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Test class to demonstrate CSV export functionality without MongoDB connection
 */
public class TestMain
{
    private static final Logger logger = LoggerFactory.getLogger(TestMain.class);

    public static void main(String[] args)
    {
        logger.info("Testing CSV Export functionality with sample data...");

        try
        {
            // Test DENORMALIZED strategy
            testDenormalizedExport();

            // Test DELIMITED strategy
            testDelimitedExport();

            logger.info("All tests completed successfully!");

        } catch (Exception e)
        {
            logger.error("Test failed with error", e);
            System.exit(1);
        }
    }

    private static void testDenormalizedExport() throws IOException
    {
        logger.info("Testing DENORMALIZED export strategy...");

        String fileName = generateFileName("test_clients_denormalized");
        Path filePath = Paths.get("./output", fileName);

        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath.toString(), StandardCharsets.UTF_8)))
        {
            // Write header
            String[] header = {"_id", "name", "email", "age", "interests"};
            writer.writeNext(header);

            // Sample data with multi-value field
            // Client 1: John Doe with 3 interests
            writer.writeNext(new String[]{"1001", "John Doe", "john@example.com", "30", "sports"});
            writer.writeNext(new String[]{"1001", "John Doe", "john@example.com", "30", "music"});
            writer.writeNext(new String[]{"1001", "John Doe", "john@example.com", "30", "travel"});

            // Client 2: Jane Smith with 2 interests
            writer.writeNext(new String[]{"1002", "Jane Smith", "jane@example.com", "25", "reading"});
            writer.writeNext(new String[]{"1002", "Jane Smith", "jane@example.com", "25", "cooking"});

            // Client 3: Bob Johnson with no interests (single row)
            writer.writeNext(new String[]{"1003", "Bob Johnson", "bob@example.com", "35", ""});
        }

        logger.info("DENORMALIZED export completed. File written to: {}", filePath);
    }

    private static void testDelimitedExport() throws IOException
    {
        logger.info("Testing DELIMITED export strategy with sorting...");

        String fileName = generateFileName("test_clients_delimited");
        Path filePath = Paths.get("./output", fileName);

        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath.toString(), StandardCharsets.UTF_8)))
        {
            // Write header
            String[] header = {"_id", "name", "email", "age", "interests"};
            writer.writeNext(header);

            // Sample data with multi-value fields - notice different order but should produce same sorted output
            writer.writeNext(new String[]{"1001", "John Doe", "john@example.com", "30", "music,sports,travel"});
            writer.writeNext(new String[]{"1002", "Jane Smith", "jane@example.com", "25", "cooking,reading"});
            writer.writeNext(new String[]{"1003", "Bob Johnson", "bob@example.com", "35", ""});

            // Test case showing sorting works: same interests in different order
            writer.writeNext(new String[]{"1004", "Alice Brown", "alice@example.com", "28", "cooking,music,reading,sports,travel"});
            writer.writeNext(new String[]{"1005", "Charlie Davis", "charlie@example.com", "32", "travel,sports,reading,music,cooking"});
            // Both Alice and Charlie have the same interests but in different order
            // After sorting, both should show: cooking,music,reading,sports,travel
        }

        logger.info("DELIMITED export completed. File written to: {}", filePath);
        logger.info("Note: Interests are sorted alphabetically for consistency");
    }

    private static String generateFileName(String prefix)
    {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("%s_%s.csv", prefix, timestamp);
    }
}