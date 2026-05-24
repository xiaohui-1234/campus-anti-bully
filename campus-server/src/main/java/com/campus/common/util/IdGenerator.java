package com.campus.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class IdGenerator {

    private static final DateTimeFormatter EVENT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private IdGenerator() {
    }

    public static String userId() {
        return "usr_" + compactUuid(16);
    }

    public static String eventId(String deviceId) {
        return "evt_" + EVENT_FORMATTER.format(LocalDateTime.now()) + "_" + deviceId + "_" + compactUuid(8);
    }

    public static String mqttMsgId(String deviceId) {
        return System.currentTimeMillis() + "-000-" + deviceId;
    }

    private static String compactUuid(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
