package com.campus.module.admin.service;

import com.campus.config.CampusProperties;
import com.campus.module.admin.dto.ConfigUpdateRequest;
import com.campus.module.admin.vo.ConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminConfigService {

    private final CampusProperties properties;

    public ConfigVO getConfig() {
        return ConfigVO.builder()
                .cache(ConfigVO.Cache.builder()
                        .mqttDedupTtlSeconds(properties.getCache().getMqttDedupTtlSeconds())
                        .deviceOnlineTtlSeconds(properties.getCache().getDeviceOnlineTtlSeconds())
                        .bindCodeTtlSeconds(properties.getCache().getBindCodeTtlSeconds())
                        .accessUrlTtlSeconds(properties.getCache().getAccessUrlTtlSeconds())
                        .build())
                .storage(ConfigVO.Storage.builder()
                        .bucket(properties.getMinio().getBucket())
                        .objectKeyPattern(properties.getStorage().getObjectKeyPattern())
                        .uploadUrlExpireSeconds(properties.getCache().getUploadUrlTtlSeconds())
                        .accessUrlExpireSeconds(properties.getCache().getAccessUrlTtlSeconds())
                        .build())
                .mqtt(ConfigVO.Mqtt.builder()
                        .brokerUrl(properties.getMqtt().getBrokerUrl())
                        .clientId(properties.getMqtt().getClientId())
                        .qos(properties.getMqtt().getQos())
                        .enabled(properties.getMqtt().isEnabled())
                        .build())
                .minio(ConfigVO.Minio.builder()
                        .endpoint(properties.getMinio().getEndpoint())
                        .accessKey(mask(properties.getMinio().getAccessKey()))
                        .secretKey(mask(properties.getMinio().getSecretKey()))
                        .build())
                .build();
    }

    public ConfigVO update(ConfigUpdateRequest request) {
        if (request.getCache() != null) {
            if (request.getCache().getMqttDedupTtlSeconds() != null) {
                properties.getCache().setMqttDedupTtlSeconds(request.getCache().getMqttDedupTtlSeconds());
            }
            if (request.getCache().getDeviceOnlineTtlSeconds() != null) {
                properties.getCache().setDeviceOnlineTtlSeconds(request.getCache().getDeviceOnlineTtlSeconds());
            }
            if (request.getCache().getBindCodeTtlSeconds() != null) {
                properties.getCache().setBindCodeTtlSeconds(request.getCache().getBindCodeTtlSeconds());
            }
            if (request.getCache().getAccessUrlTtlSeconds() != null) {
                properties.getCache().setAccessUrlTtlSeconds(request.getCache().getAccessUrlTtlSeconds());
            }
        }
        if (request.getStorage() != null) {
            if (StringUtils.hasText(request.getStorage().getObjectKeyPattern())) {
                properties.getStorage().setObjectKeyPattern(request.getStorage().getObjectKeyPattern());
            }
            if (request.getStorage().getUploadUrlExpireSeconds() != null) {
                properties.getCache().setUploadUrlTtlSeconds(request.getStorage().getUploadUrlExpireSeconds());
            }
            if (request.getStorage().getAccessUrlExpireSeconds() != null) {
                properties.getCache().setAccessUrlTtlSeconds(request.getStorage().getAccessUrlExpireSeconds());
            }
        }
        return getConfig();
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
