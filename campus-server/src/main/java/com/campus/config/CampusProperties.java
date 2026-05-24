package com.campus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "campus")
public class CampusProperties {

    private Security security = new Security();
    private Wx wx = new Wx();
    private Mqtt mqtt = new Mqtt();
    private Minio minio = new Minio();
    private Cache cache = new Cache();
    private Storage storage = new Storage();
    private Event event = new Event();

    @Data
    public static class Security {
        private Jwt jwt = new Jwt();
    }

    @Data
    public static class Jwt {
        private long accessTokenExpireSeconds = 7200;
        private long refreshTokenExpireSeconds = 604800;
        private String secret = "please-change-this-secret-at-least-32-bytes";
    }

    @Data
    public static class Wx {
        private Miniapp miniapp = new Miniapp();
    }

    @Data
    public static class Miniapp {
        private String appId;
        private String appSecret;
    }

    @Data
    public static class Mqtt {
        private boolean enabled;
        private String brokerUrl;
        private String clientId;
        private String username;
        private String password;
        private int qos = 1;
        private long completionTimeoutMs = 5000;
    }

    @Data
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        private String publicEndpoint;
    }

    @Data
    public static class Cache {
        private long mqttDedupTtlSeconds = 86400;
        private long deviceOnlineTtlSeconds = 90;
        private long bindCodeTtlSeconds = 60;
        private long accessUrlTtlSeconds = 3600;
        private long uploadUrlTtlSeconds = 300;
        private long jwtBlacklistTtlSeconds = 7200;
        private long unpulledEventCacheTtlSeconds = 300;
    }

    @Data
    public static class Storage {
        private String objectKeyPattern;
        private String avatarKeyPattern;
        private long avatarMaxSizeMb = 2;
    }

    @Data
    public static class Event {
        private long defaultPageSize = 10;
        private long maxPageSize = 100;
    }
}
