package com.campus.module.mqtt.dto;

import lombok.Data;

@Data
public class WifiSetReplyPayload {

    private String mqttMsgId;
    private Integer configId;
    private Boolean success;
}
