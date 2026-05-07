package com.interactive.edu.service.storage;

import com.interactive.edu.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "minio")
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final Object bucketInitLock = new Object();
    private volatile boolean bucketInitialized;

    @Override
    public StoredObject save(String coursewareId, MultipartFile file) {
        try {
            ensureBucket();

            String originalFilename = file.getOriginalFilename();
            String filename = originalFilename == null || originalFilename.isBlank()
                    ? "courseware.bin"
                    : Path.of(originalFilename).getFileName().toString();
            String objectKey = coursewareId + "/" + filename;

            try (InputStream in = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(minioProperties.getBucket())
                                .object(objectKey)
                                .stream(in, file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );
            }

            return new StoredObject(objectKey, "minio");
        } catch (Exception e) {
            throw new RuntimeException("MinIO 存储失败: " + e.getMessage(), e);
        }
    }

    @PostConstruct
    public void initializeBucket() {
        try {
            ensureBucket();
        } catch (Exception e) {
            throw new IllegalStateException("MinIO bucket 初始化失败: " + e.getMessage(), e);
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketInitialized) {
            return;
        }

        synchronized (bucketInitLock) {
            if (bucketInitialized) {
                return;
            }

            String bucket = minioProperties.getBucket();
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );
            if (!exists) {
                try {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                } catch (Exception e) {
                    boolean createdByAnotherThread = minioClient.bucketExists(
                            BucketExistsArgs.builder().bucket(bucket).build()
                    );
                    if (!createdByAnotherThread) {
                        throw e;
                    }
                }
            }

            bucketInitialized = true;
        }
    }
}
