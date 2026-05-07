package com.interactive.edu.service.storage;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StoredObject {
    /**
     * For MinIO: objectKey, like coursewareId/filename.pdf
     * For local: relative path, like coursewareId/filename.pdf
     */
    private String key;

    /**
     * minio | local
     */
    private String storageType;
}
