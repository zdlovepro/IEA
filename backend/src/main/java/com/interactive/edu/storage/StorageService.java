package com.interactive.edu.service.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    StoredObject save(String coursewareId, MultipartFile file);
}