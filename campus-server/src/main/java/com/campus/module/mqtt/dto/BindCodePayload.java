package com.campus.module.mqtt.dto;

import lombok.Data;

@Data
public class BindCodePayload {

    private String mqttMsgId;
    private String productType;
    private String deviceId;
    private String bindCode;
}
