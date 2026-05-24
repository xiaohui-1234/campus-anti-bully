package com.campus.module.mqtt.topic;

import com.campus.common.exception.BizException;
import com.campus.module.mqtt.dto.MqttMessageEnvelope;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class MqttTopicBuilder {

    public MqttMessageEnvelope parse(String topic, String payload, String mqttMsgId) {
        String[] parts = topic.split("/");
        if (parts.length < 4 || !"device".equals(parts[0])) {
            throw new BizException(-1, "MQTT topic 格式错误");
        }
        String action = Arrays.stream(parts).skip(3).collect(Collectors.joining("/"));
        return new MqttMessageEnvelope(topic, payload, parts[1], parts[2], action, mqttMsgId);
    }

    public String alarmUpload(String productType, String deviceId) {
        return "device/" + productType + "/" + deviceId + "/alarm/upload";
    }

    public String wifiSet(String productType, String deviceId) {
        return "device/" + productType + "/" + deviceId + "/config/wifi/set";
    }

    public String subscription(String action) {
        return "device/+/+/" + action;
    }
}
