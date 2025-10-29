package com.tsu.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Google Cloud Storage
 */
@Data
@Component
@ConfigurationProperties(prefix = "gcs")
public class GcsConfig {

    private String name;
    /**
     * GCP Project ID
     */
    private String projectId;


    /**
     * Path to service account credentials JSON file (optional)
     * If not provided, will use Application Default Credentials
     */
    private String credentialsPath;

    /**
     * Whether to use uniform bucket-level access (recommended: true)
     */
    private boolean uniformBucketLevelAccess = true;

    /**
     * Whether to enable public access prevention (recommended: true)
     */
    private boolean publicAccessPrevention = true;

    /**
     * Connection timeout in milliseconds
     */
    private int connectTimeout = 60000;

    /**
     * Read timeout in milliseconds
     */
    private int readTimeout = 60000;
}
