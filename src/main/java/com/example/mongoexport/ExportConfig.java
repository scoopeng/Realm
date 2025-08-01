package com.example.mongoexport;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ExportConfig
{
    private static final Logger logger = LoggerFactory.getLogger(ExportConfig.class);

    private final Config config;
    private final String mongoUrl;
    private final String databaseName;
    private final String outputDirectory;
    private final ExportStrategy exportStrategy;
    private final String currentEnvironment;

    public ExportConfig()
    {
        this.config = ConfigFactory.load();
        this.currentEnvironment = config.getString("current.environment");
        this.mongoUrl = loadMongoUrl();
        this.databaseName = config.getString("database.name");
        this.outputDirectory = config.getString("output.directory");
        this.exportStrategy = ExportStrategy.valueOf(config.getString("export.strategy"));

        createOutputDirectoryIfNeeded();

        logger.info("Configuration loaded successfully");
        logger.info("Environment: {}", currentEnvironment);
        logger.info("Database: {}", databaseName);
        logger.info("Output directory: {}", outputDirectory);
        logger.info("Export strategy: {}", exportStrategy);
    }

    private String loadMongoUrl()
    {
        String urlKey = String.format("mongodb.url.%s", currentEnvironment);
        String url = config.getString(urlKey);

        if (url.contains("<username>") || url.contains("<password>") || url.contains("<host>"))
        {
            logger.warn("MongoDB URL contains placeholder values. Please update application.properties with actual credentials.");
        }

        return url;
    }

    private void createOutputDirectoryIfNeeded()
    {
        try
        {
            Path outputPath = Paths.get(outputDirectory);
            if (!Files.exists(outputPath))
            {
                Files.createDirectories(outputPath);
                logger.info("Created output directory: {}", outputDirectory);
            }
        } catch (IOException e)
        {
            throw new RuntimeException("Failed to create output directory: " + outputDirectory, e);
        }
    }

    public String getMongoUrl()
    {
        return mongoUrl;
    }

    public String getDatabaseName()
    {
        return databaseName;
    }

    public String getOutputDirectory()
    {
        return outputDirectory;
    }

    public ExportStrategy getExportStrategy()
    {
        return exportStrategy;
    }

    public String getCurrentEnvironment()
    {
        return currentEnvironment;
    }
}