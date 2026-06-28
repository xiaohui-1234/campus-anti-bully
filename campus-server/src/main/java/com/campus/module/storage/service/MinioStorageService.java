package com.campus.module.storage.service;

import com.campus.common.exception.BizException;
import com.campus.common.util.FileUtil;
import com.campus.config.CampusProperties;
import com.campus.module.device.entity.Device;
import com.campus.module.storage.dto.PresignedAccessInfo;
import com.campus.module.storage.dto.PresignedUploadInfo;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MinioStorageService implements StorageService {

    private final MinioClient internalMinioClient;
    private final MinioClient publicMinioClient;
    private final MinioClient uploadMinioClient;
    private final CampusProperties properties;

    public MinioStorageService(@Qualifier("internalMinioClient") MinioClient internalMinioClient,
                               @Qualifier("publicMinioClient") MinioClient publicMinioClient,
                               @Qualifier("uploadMinioClient") MinioClient uploadMinioClient,
                               CampusProperties properties) {
        this.internalMinioClient = internalMinioClient;
        this.publicMinioClient = publicMinioClient;
        this.uploadMinioClient = uploadMinioClient;
        this.properties = properties;
    }

    @Override
    public PresignedUploadInfo createUploadUrl(Device device, String eventId, String filename, String contentType) {
        try {
            ensureBucket();
            String ext = FileUtil.extension(filename, defaultExt(contentType));
            String objectKey = buildObjectKey(device, eventId, ext);
            int expireSeconds = (int) properties.getCache().getUploadUrlTtlSeconds();
            String uploadUrl = uploadMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(properties.getMinio().getBucket())
                    .object(objectKey)
                    .expiry(expireSeconds, TimeUnit.SECONDS)
                    .build());
            log.info("Generated upload url for event_id={}, object_key={}", eventId, objectKey);
            return new PresignedUploadInfo(eventId, objectKey, uploadUrl, expireSeconds);
        } catch (Exception ex) {
            log.error("Generate upload url failed, event_id={}", eventId, ex);
            throw new BizException(500, "生成上传地址失败");
        }
    }

    @Override
    public PresignedAccessInfo createAccessUrl(String fileKey, int expireSeconds) {
        try {
            String fileUrl = publicMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getMinio().getBucket())
                    .object(fileKey)
                    .expiry(expireSeconds, TimeUnit.SECONDS)
                    .build());
            return new PresignedAccessInfo(fileUrl, expireSeconds, LocalDateTime.now().plusSeconds(expireSeconds));
        } catch (Exception ex) {
            log.error("Generate access url failed, object_key={}", fileKey, ex);
            throw new BizException(500, "生成访问地址失败");
        }
    }

    @Override
    public String uploadAvatar(String userId, MultipartFile file, String ext) {
        try {
            ensureBucket();
            String timestamp = String.valueOf(System.currentTimeMillis());
            String uuid = UUID.randomUUID().toString().replace("-", "");
            String version = timestamp + "-" + uuid;
            String key = buildAvatarKey(userId, ext, timestamp, uuid, version);
            internalMinioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .object(key)
                    .contentType(file.getContentType())
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .build());
            String avatarUrl = trimSlash(properties.getMinio().getEffectivePublicEndpoint()) + "/" + properties.getMinio().getBucket() + "/" + key;
            return avatarKeyPatternHasVersion() ? avatarUrl : avatarUrl + "?v=" + version;
        } catch (Exception ex) {
            log.error("Upload avatar failed, user_id={}", userId, ex);
            throw new BizException(500, "头像上传失败");
        }
    }

    private void ensureBucket() throws Exception {
        String bucket = properties.getMinio().getBucket();
        boolean exists = internalMinioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            internalMinioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private String buildObjectKey(Device device, String eventId, String ext) {
        LocalDate now = LocalDate.now();
        Map<String, String> values = Map.of(
                "product_type", device.getProductType(),
                "device_id", device.getDeviceId(),
                "yyyy", String.valueOf(now.getYear()),
                "MM", String.format("%02d", now.getMonthValue()),
                "dd", String.format("%02d", now.getDayOfMonth()),
                "event_id", eventId,
                "ext", ext
        );
        String key = properties.getStorage().getObjectKeyPattern();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            key = key.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return key;
    }

    private String buildAvatarKey(String userId, String ext, String timestamp, String uuid, String version) {
        LocalDate now = LocalDate.now();
        Map<String, String> values = Map.of(
                "user_id", userId,
                "yyyy", String.valueOf(now.getYear()),
                "MM", String.format("%02d", now.getMonthValue()),
                "dd", String.format("%02d", now.getDayOfMonth()),
                "timestamp", timestamp,
                "uuid", uuid,
                "version", version,
                "ext", ext
        );
        String key = properties.getStorage().getAvatarKeyPattern();
        if (key == null || key.isBlank()) {
            key = "avatar/{user_id}/{timestamp}-{uuid}.{ext}";
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            key = key.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return key;
    }

    private boolean avatarKeyPatternHasVersion() {
        String key = properties.getStorage().getAvatarKeyPattern();
        return key != null && (key.contains("{timestamp}") || key.contains("{uuid}") || key.contains("{version}"));
    }

    private String defaultExt(String contentType) {
        if (contentType != null && contentType.contains("mpeg")) {
            return "mp3";
        }
        return "wav";
    }

    private String trimSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

}
