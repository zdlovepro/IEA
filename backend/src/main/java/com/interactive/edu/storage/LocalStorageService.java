package com.interactive.edu.service.storage;

import com.interactive.edu.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private final StorageProperties storageProperties;

    @Override
    public StoredObject save(String coursewareId, MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            String filename;
            if (originalFilename == null || originalFilename.isBlank()) {
                filename = "courseware.bin";
            } else {
                filename = Path.of(originalFilename).getFileName().toString();
                if (filename.isBlank()) {
                    filename = "courseware.bin";
                }
            }
            Path baseDir = Path.of(storageProperties.getLocalBaseDir()).toAbsolutePath().normalize();
            Path courseDir = baseDir.resolve(coursewareId).normalize();
            Files.createDirectories(courseDir);

            Path dest = courseDir.resolve(filename).normalize();
            // basic safety: ensure still under the current course directory
            if (!dest.startsWith(courseDir)) {
                throw new IllegalArgumentException("非法文件路径");
            }

            file.transferTo(dest.toFile());
            String rel = coursewareId + "/" + filename;
            return new StoredObject(rel, "local");
        } catch (Exception e) {
            throw new RuntimeException("本地存储失败: " + e.getMessage(), e);
        }
    }

    // 可选：清理用（暂未用到）
    public void deleteCoursewareDir(String coursewareId) {
        try {
            Path baseDir = Path.of(storageProperties.getLocalBaseDir()).toAbsolutePath().normalize();
            File dir = baseDir.resolve(coursewareId).toFile();
            FileSystemUtils.deleteRecursively(dir);
        } catch (Exception ignored) {}
    }
}
