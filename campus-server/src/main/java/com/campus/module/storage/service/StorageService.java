package com.campus.module.storage.service;

import com.campus.module.device.entity.Device;
import com.campus.module.storage.dto.PresignedAccessInfo;
import com.campus.module.storage.dto.PresignedUploadInfo;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    PresignedUploadInfo createUploadUrl(Device device, String eventId, String filename, String contentType);

    PresignedAccessInfo createAccessUrl(String fileKey, int expireSeconds);

    String uploadAvatar(String userId, MultipartFile file, String ext);
}
