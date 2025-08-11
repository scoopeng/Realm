package com.example.mongoexport;

import com.example.mongoexport.config.DiscoveryConfiguration;
import com.example.mongoexport.export.ConfigurationBasedExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Main runner for the configuration-based export phase.
 * This reads a JSON configuration file and exports data according to its settings.
 */
public class ConfigExportRunner
{
    private static final Logger logger = LoggerFactory.getLogger(ConfigExportRunner.class);

    public static void main(String[] args)
    {
        if (args.length < 1)
        {
            System.err.println("Usage: ConfigExportRunner <collection> [rowLimit]");
            System.err.println("Example: ConfigExportRunner listings");
            System.err.println("Example: ConfigExportRunner listings 1000");
            System.exit(1);
        }

        String collectionName = args[0];
        Integer rowLimit = null;

        if (args.length > 1)
        {
            try
            {
                rowLimit = Integer.parseInt(args[1]);
                logger.info("Row limit set to: {}", rowLimit);
            } catch (NumberFormatException e)
            {
                logger.error("Invalid row limit: {}", args[1]);
                System.exit(1);
            }
        }

        try
        {
            logger.info("=== CONFIGURATION-BASED EXPORT PHASE ===");
            logger.info("Collection: {}", collectionName);

            // Load configuration file
            File configFile = DiscoveryConfiguration.getConfigFile(collectionName);
            if (!configFile.exists())
            {
                logger.error("Configuration file not found: {}", configFile.getAbsolutePath());
                logger.error("Please run discovery phase first: ./gradlew discover -Pcollection={}", collectionName);
                System.exit(1);
            }

            logger.info("Loading configuration from: {}", configFile.getAbsolutePath());

            // Run export
            ConfigurationBasedExporter exporter = new ConfigurationBasedExporter(configFile);
            if (rowLimit != null)
            {
                exporter.setRowLimit(rowLimit);
            }
            exporter.export();

            logger.info("\n=== EXPORT COMPLETE ===");
            logger.info("CSV file has been generated in the output directory");

        } catch (Exception e)
        {
            logger.error("Export failed", e);
            System.exit(1);
        }
    }
}