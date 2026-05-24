package com.campus.module.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConfigVO {

    private Cache cache;
    private Storage storage;
    private Mqtt mqtt;
    private Minio minio;

    @Data
    @Builder
    public static class Cache {
        private long mqttDedupTtlSeconds;
        private long deviceOnlineTtlSeconds;
        private long bindCodeTtlSeconds;
        private long accessUrlTtlSeconds;
    }

    @Data
    @Builder
    public static class Storage {
        private String bucket;
        private String objectKeyPattern;
        private long uploadUrlExpireSeconds;
        private long accessUrlExpireSeconds;
    }

    @Data
    @Builder
    public static class Mqtt {
        private String brokerUrl;
        private String clientId;
        private int qos;
        private boolean enabled;
    }

    @Data
    @Builder
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
    }
}
