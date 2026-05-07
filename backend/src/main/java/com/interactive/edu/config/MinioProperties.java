package com.interactive.edu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    /** MinIO endpoint, e.g. http://localhost:9000. */
    private String endpoint;

    private String accessKey;

    private String secretKey;

    /** Default bucket name. */
    private String bucket = "courseware";

    /** Whether the endpoint uses HTTPS. */
    private boolean secure = false;
}
