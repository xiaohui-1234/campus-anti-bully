package com.campus.module.mqtt.dto;

import lombok.Data;

import java.util.List;

@Data
public class WifiListPayload {

    private String mqttMsgId;
    private Integer maxSize;
    private Integer idUsing;
    private List<WifiConfig> config;

    @Data
    public static class WifiConfig {
        private Integer configId;
        private String wifiName;
    }
}
