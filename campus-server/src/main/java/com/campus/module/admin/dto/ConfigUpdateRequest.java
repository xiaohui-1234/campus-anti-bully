package com.campus.module.admin.dto;

import lombok.Data;

@Data
public class ConfigUpdateRequest {

    private Cache cache;
    private Storage storage;

    @Data
    public static class Cache {
        private Long mqttDedupTtlSeconds;
        private Long deviceOnlineTtlSeconds;
        private Long bindCodeTtlSeconds;
        private Long accessUrlTtlSeconds;
    }

    @Data
    public static class Storage {
        private String objectKeyPattern;
        private Long uploadUrlExpireSeconds;
        private Long accessUrlExpireSeconds;
    }
}
