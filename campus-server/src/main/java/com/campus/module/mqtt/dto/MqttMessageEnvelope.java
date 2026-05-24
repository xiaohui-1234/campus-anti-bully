package com.campus.module.mqtt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MqttMessageEnvelope {

    private String topic;
    private String payload;
    private String productType;
    private String deviceId;
    private String action;
    private String mqttMsgId;
}
