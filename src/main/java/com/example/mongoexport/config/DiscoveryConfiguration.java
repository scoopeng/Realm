package com.example.mongoexport.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Root configuration containing all discovered fields and export settings.
 * This is the main configuration file that will be saved/loaded for the two-phase export process.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiscoveryConfiguration
{
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryConfiguration.class);

    @JsonProperty("collection")
    private String collection;

    @JsonProperty("discoveredAt")
    private Date discoveredAt;

    @JsonProperty("discoveryParameters")
    private DiscoveryParameters discoveryParameters;

    @JsonProperty("fields")
    private List<FieldConfiguration> fields = new ArrayList<>();

    @JsonProperty("requiredCollections")
    private List<String> requiredCollections = new ArrayList<>(); // Collections that need to be cached

    @JsonProperty("exportSettings")
    private ExportSettings exportSettings;

    // Discovery parameters inner class
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DiscoveryParameters
    {
        @JsonProperty("sampleSize")
        private int sampleSize = 10000;

        @JsonProperty("expansionDepth")
        private int expansionDepth = 3;

        @JsonProperty("minDistinctNonNullValues")
        private int minDistinctNonNullValues = 2;

        @JsonProperty("includeBusinessIds")
        private boolean includeBusinessIds = true;

        // Getters and setters
        public int getSampleSize()
        {
            return sampleSize;
        }

        public void setSampleSize(int size)
        {
            this.sampleSize = size;
        }

        public int getExpansionDepth()
        {
            return expansionDepth;
        }

        public void setExpansionDepth(int depth)
        {
            this.expansionDepth = depth;
        }

        public int getMinDistinctNonNullValues()
        {
            return minDistinctNonNullValues;
        }

        public void setMinDistinctNonNullValues(int min)
        {
            this.minDistinctNonNullValues = min;
        }

        public boolean isIncludeBusinessIds()
        {
            return includeBusinessIds;
        }

        public void setIncludeBusinessIds(boolean include)
        {
            this.includeBusinessIds = include;
        }
    }

    // Export settings inner class
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExportSettings
    {
        @JsonProperty("batchSize")
        private int batchSize = 5000;

        @JsonProperty("useBusinessNames")
        private boolean useBusinessNames = true;

        @JsonProperty("outputFormat")
        private String outputFormat = "csv";

        @JsonProperty("csvDelimiter")
        private String csvDelimiter = ",";

        @JsonProperty("csvQuoteChar")
        private String csvQuoteChar = "\"";

        // Getters and setters
        public int getBatchSize()
        {
            return batchSize;
        }

        public void setBatchSize(int size)
        {
            this.batchSize = size;
        }

        public boolean isUseBusinessNames()
        {
            return useBusinessNames;
        }

        public void setUseBusinessNames(boolean use)
        {
            this.useBusinessNames = use;
        }

        public String getOutputFormat()
        {
            return outputFormat;
        }

        public void setOutputFormat(String format)
        {
            this.outputFormat = format;
        }

        public String getCsvDelimiter()
        {
            return csvDelimiter;
        }

        public void setCsvDelimiter(String delimiter)
        {
            this.csvDelimiter = delimiter;
        }

        public String getCsvQuoteChar()
        {
            return csvQuoteChar;
        }

        public void setCsvQuoteChar(String quote)
        {
            this.csvQuoteChar = quote;
        }
    }

    // Constructors
    public DiscoveryConfiguration()
    {
        this.discoveryParameters = new DiscoveryParameters();
        this.exportSettings = new ExportSettings();
    }

    public DiscoveryConfiguration(String collection)
    {
        this();
        this.collection = collection;
        this.discoveredAt = new Date();
    }

    // Helper methods

    /**
     * Save configuration to JSON file
     */
    public void saveToFile(File file) throws IOException
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(file, this);
    }

    /**
     * Load configuration from JSON file
     */
    public static DiscoveryConfiguration loadFromFile(File file) throws IOException
    {
        ObjectMapper mapper = new ObjectMapper();
        DiscoveryConfiguration config = mapper.readValue(file, DiscoveryConfiguration.class);
        
        // Check for supplemental configuration
        File supplementalFile = getSupplementalConfigFile(config.getCollection());
        if (supplementalFile.exists()) {
            logger.info("Loading supplemental configuration from: {}", supplementalFile.getAbsolutePath());
            DiscoveryConfiguration supplemental = mapper.readValue(supplementalFile, DiscoveryConfiguration.class);
            config.mergeSupplemental(supplemental);
        }
        
        return config;
    }

    /**
     * Get configuration file path for a collection
     */
    public static File getConfigFile(String collection)
    {
        return new File("config", collection + "_fields.json");
    }
    
    /**
     * Get supplemental configuration file path for a collection
     */
    public static File getSupplementalConfigFile(String collection)
    {
        return new File("config", collection + "_supplemental.json");
    }

    /**
     * Find a field configuration by path
     */
    public FieldConfiguration findFieldByPath(String fieldPath)
    {
        return fields.stream()
                .filter(f -> f.getFieldPath().equals(fieldPath))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all fields that should be included in export
     */
    @JsonIgnore
    public List<FieldConfiguration> getIncludedFields()
    {
        return fields.stream()
                .filter(FieldConfiguration::isInclude)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Add a required collection for caching
     */
    public void addRequiredCollection(String collectionName)
    {
        if (!requiredCollections.contains(collectionName))
        {
            requiredCollections.add(collectionName);
        }
    }
    
    /**
     * Merge supplemental configuration into this configuration
     * Supplemental fields are added, existing fields can be updated
     */
    public void mergeSupplemental(DiscoveryConfiguration supplemental) {
        if (supplemental == null) return;
        
        logger.info("Merging {} supplemental fields into configuration", 
                   supplemental.getFields() != null ? supplemental.getFields().size() : 0);
        
        // Add new fields from supplemental
        if (supplemental.getFields() != null) {
            for (FieldConfiguration suppField : supplemental.getFields()) {
                FieldConfiguration existing = findFieldByPath(suppField.getFieldPath());
                if (existing == null) {
                    // New field - add it
                    fields.add(suppField);
                    logger.debug("Added supplemental field: {}", suppField.getFieldPath());
                } else {
                    // Existing field - update include flag if supplemental says to include
                    if (suppField.isInclude() && !existing.isInclude()) {
                        existing.setInclude(true);
                        logger.debug("Enabled field from supplemental: {}", suppField.getFieldPath());
                    }
                }
            }
        }
        
        // Merge required collections
        if (supplemental.getRequiredCollections() != null) {
            for (String collection : supplemental.getRequiredCollections()) {
                addRequiredCollection(collection);
            }
        }
        
        // Merge discovery parameters if specified
        if (supplemental.getDiscoveryParameters() != null) {
            // Only override specific parameters if set in supplemental
            DiscoveryParameters suppParams = supplemental.getDiscoveryParameters();
            if (suppParams.getExpansionDepth() > 0) {
                if (discoveryParameters == null) {
                    discoveryParameters = new DiscoveryParameters();
                }
                discoveryParameters.setExpansionDepth(suppParams.getExpansionDepth());
            }
        }
        
        logger.info("Configuration after merge: {} total fields, {} included", 
                   fields.size(), getIncludedFields().size());
    }

    // Main getters and setters
    public String getCollection()
    {
        return collection;
    }

    public void setCollection(String collection)
    {
        this.collection = collection;
    }

    public Date getDiscoveredAt()
    {
        return discoveredAt;
    }

    public void setDiscoveredAt(Date date)
    {
        this.discoveredAt = date;
    }

    public DiscoveryParameters getDiscoveryParameters()
    {
        return discoveryParameters;
    }

    public void setDiscoveryParameters(DiscoveryParameters params)
    {
        this.discoveryParameters = params;
    }

    public List<FieldConfiguration> getFields()
    {
        return fields;
    }

    public void setFields(List<FieldConfiguration> fields)
    {
        this.fields = fields;
    }

    public List<String> getRequiredCollections()
    {
        return requiredCollections;
    }

    public void setRequiredCollections(List<String> collections)
    {
        this.requiredCollections = collections;
    }

    public ExportSettings getExportSettings()
    {
        return exportSettings;
    }

    public void setExportSettings(ExportSettings settings)
    {
        this.exportSettings = settings;
    }
}