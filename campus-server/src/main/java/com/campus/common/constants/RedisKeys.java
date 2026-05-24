package com.campus.common.constants;

public final class RedisKeys {

    private RedisKeys() {
    }

    public static String mqttDedup(String deviceId, String mqttMsgId) {
        return "mqtt:dedup:" + deviceId + ":" + mqttMsgId;
    }

    public static String deviceOnline(String deviceId) {
        return "device:online:" + deviceId;
    }

    public static String deviceLastHeartbeat(String deviceId) {
        return "device:last_heartbeat:" + deviceId;
    }

    public static String deviceBindCode(String deviceId) {
        return "device:bind_code:" + deviceId;
    }

    public static String jwtBlacklist(String tokenId) {
        return "jwt:blacklist:" + tokenId;
    }

    public static String eventUnpulledUser(String userId) {
        return "event:unpulled:user:" + userId;
    }

    public static String minioAccessUrl(String eventId) {
        return "minio:access_url:" + eventId;
    }

    public static String wsUser(String userId) {
        return "ws:user:" + userId;
    }

    public static String wsDevice(String deviceId) {
        return "ws:device:" + deviceId;
    }
}
