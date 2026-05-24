package com.campus.module.mqtt.router;

import com.campus.module.mqtt.dedup.MqttDedupService;
import com.campus.module.mqtt.dto.MqttMessageEnvelope;
import com.campus.module.mqtt.queue.MqttMessageQueue;
import com.campus.module.mqtt.topic.MqttTopicBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttRouter {

    private final ObjectMapper objectMapper;
    private final MqttTopicBuilder topicBuilder;
    private final MqttDedupService dedupService;
    private final MqttMessageQueue queue;

    public void route(String topic, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String mqttMsgId = text(root, "mqtt_msg_id");
            String productType = text(root, "product_type");
            String deviceId = text(root, "device_id");
            MqttMessageEnvelope envelope = topicBuilder.parse(topic, payload, mqttMsgId);
            if (StringUtils.hasText(productType) && !productType.equals(envelope.getProductType())) {
                log.warn("MQTT product_type mismatch, topic={}, mqtt_msg_id={}", topic, mqttMsgId);
                return;
            }
            if (StringUtils.hasText(deviceId) && !deviceId.equals(envelope.getDeviceId())) {
                log.warn("MQTT device_id mismatch, topic={}, mqtt_msg_id={}", topic, mqttMsgId);
                return;
            }
            if (!StringUtils.hasText(mqttMsgId)) {
                log.warn("MQTT message ignored because mqtt_msg_id missing, topic={}", topic);
                return;
            }
            if (dedupService.firstSeen(envelope.getDeviceId(), mqttMsgId)) {
                queue.offer(envelope);
            }
        } catch (Exception ex) {
            log.warn("MQTT route failed, topic={}", topic, ex);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
