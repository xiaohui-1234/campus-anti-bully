package com.campus.module.mqtt.publisher;

import com.campus.config.CampusProperties;
import com.campus.module.mqtt.topic.MqttTopicBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttPublisher {

    private final CampusProperties properties;
    private final MqttClient mqttClient;
    private final MqttTopicBuilder topicBuilder;
    private final ObjectMapper objectMapper;

    public void publishAlarmUpload(String productType, String deviceId, Object payload) {
        publish(topicBuilder.alarmUpload(productType, deviceId), payload);
    }

    public void publishWifiSet(String productType, String deviceId, Object payload) {
        publish(topicBuilder.wifiSet(productType, deviceId), payload);
    }

    private void publish(String topic, Object payload) {
        if (!properties.getMqtt().isEnabled() || !mqttClient.isConnected()) {
            log.info("MQTT publish skipped, topic={}, mqtt_enabled={}, connected={}",
                    topic, properties.getMqtt().isEnabled(), mqttClient.isConnected());
            return;
        }
        try {
            byte[] body = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            MqttMessage message = new MqttMessage(body);
            message.setQos(properties.getMqtt().getQos());
            mqttClient.publish(topic, message);
            log.info("MQTT published, topic={}", topic);
        } catch (Exception ex) {
            log.error("MQTT publish failed, topic={}", topic, ex);
        }
    }
}
