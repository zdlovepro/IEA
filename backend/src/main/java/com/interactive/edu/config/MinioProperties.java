package com.interactive.edu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    /** MinIO endpoint, e.g. http://localhost:9000 */
    private String endpoint;
    private String accessKey;
    private String secretKey;
    /** default bucket name */
    private String bucket;
    /** use https */
    private boolean secure = false;
}