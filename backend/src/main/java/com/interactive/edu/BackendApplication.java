package com.interactive.edu;

import com.interactive.edu.config.PythonClientProperties;
import com.interactive.edu.config.StorageProperties;
import com.interactive.edu.config.TtsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        StorageProperties.class,
        PythonClientProperties.class,
        TtsProperties.class
})
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
