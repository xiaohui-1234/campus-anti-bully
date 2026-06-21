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

    public static String emailCode(String scene, String emailHash) {
        return "auth:email_code:" + scene + ":" + emailHash;
    }

    public static String emailCodeResendLimit(String scene, String emailHash) {
        return "auth:email_code_limit:" + scene + ":" + emailHash;
    }

    public static String emailCodeAttempt(String scene, String emailHash) {
        return "auth:email_code_attempt:" + scene + ":" + emailHash;
    }

    public static String emailCodeIpLimit(String scene, String ipHash) {
        return "auth:email_code_ip_limit:" + scene + ":" + ipHash;
    }

    public static String loginFailSubject(String subjectHash) {
        return "auth:login_fail:subject:" + subjectHash;
    }

    public static String loginFailIp(String ipHash) {
        return "auth:login_fail:ip:" + ipHash;
    }

    public static String securityEmailChangeGrant(String userId, String ticketHash) {
        return "auth:security_email_change_grant:" + userId + ":" + ticketHash;
    }

    public static String securityEmailChangeCurrent(String userId) {
        return "auth:security_email_change_current:" + userId;
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
