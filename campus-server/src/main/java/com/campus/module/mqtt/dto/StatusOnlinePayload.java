package com.campus.module.mqtt.dto;

import lombok.Data;

@Data
public class StatusOnlinePayload {

    private String mqttMsgId;
    private String productType;
    private String deviceId;
    private String status;
    private Long timestamp;
}
