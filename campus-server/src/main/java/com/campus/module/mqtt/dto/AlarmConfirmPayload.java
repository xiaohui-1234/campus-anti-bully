package com.campus.module.mqtt.dto;

import lombok.Data;

@Data
public class AlarmConfirmPayload {

    private String mqttMsgId;
    private String productType;
    private String deviceId;
    private String eventId;
    private String objectKey;
    private String uploadStatus;
}
