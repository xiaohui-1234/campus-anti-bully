package com.campus.module.mqtt.handler;

import com.campus.module.mqtt.dto.MqttMessageEnvelope;
import com.campus.module.mqtt.dto.WifiSetReplyPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WifiSetReplyHandler implements MqttMessageHandler {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String action) {
        return "config/wifi/set/reply".equals(action);
    }

    @Override
    public void handle(MqttMessageEnvelope envelope) {
        try {
            WifiSetReplyPayload payload = objectMapper.readValue(envelope.getPayload(), WifiSetReplyPayload.class);
            log.info("WiFi set reply received, device_id={}, config_id={}, success={}",
                    envelope.getDeviceId(), payload.getConfigId(), payload.getSuccess());
        } catch (Exception ex) {
            log.warn("Handle wifi set reply failed, mqtt_msg_id={}", envelope.getMqttMsgId(), ex);
        }
    }
}
