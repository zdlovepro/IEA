package com.interactive.edu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "python.client")
public class PythonClientProperties {
    /** Python service base URL, e.g. http://localhost:8001 */
    private String baseUrl = "http://localhost:8001";

    /** Parse API path, e.g. /python/v1/parse */
    private String parsePath = "/python/v1/parse";

    /** Script generation API path, e.g. /python/v1/script/generate */
    private String scriptGeneratePath = "/python/v1/script/generate";

    /** HTTP connect timeout. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** HTTP read timeout. */
    private Duration readTimeout = Duration.ofSeconds(30);
}
