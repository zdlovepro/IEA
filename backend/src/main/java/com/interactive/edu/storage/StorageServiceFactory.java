package com.interactive.edu.service.storage;

import com.interactive.edu.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StorageServiceFactory {

    private final StorageProperties storageProperties;
    private final ObjectProvider<MinioStorageService> minioStorageServiceProvider;
    private final ObjectProvider<LocalStorageService> localStorageServiceProvider;

    public StorageService get() {
        String type = storageProperties.getType();
        if (type == null) {
            type = "local";
        }
        return switch (type.toLowerCase()) {
            case "local" -> requireStorage(localStorageServiceProvider.getIfAvailable(), "local");
            case "minio" -> requireStorage(minioStorageServiceProvider.getIfAvailable(), "minio");
            default -> throw new IllegalArgumentException("不支持的 storage.type: " + type);
        };
    }

    private StorageService requireStorage(StorageService service, String type) {
        if (service == null) {
            throw new IllegalStateException("未找到可用的 " + type + " 存储实现，请检查 storage.type 配置");
        }
        return service;
    }
}
