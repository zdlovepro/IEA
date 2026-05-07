package com.interactive.edu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {
    /** Supported values: local, minio. */
    private String type = "local";

    /** Base directory used when storage.type=local. */
    private String localBaseDir = "./data/courseware";
}
