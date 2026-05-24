package com.campus.module.mqtt.dto;

import lombok.Data;

@Data
public class AlarmPostPayload {

    private String mqttMsgId;
    private String productType;
    private String deviceId;
    private String eventType;
    private String alarmInfo;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private Long timestamp;
}
