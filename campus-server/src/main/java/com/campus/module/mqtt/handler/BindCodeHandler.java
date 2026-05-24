package com.campus.module.mqtt.handler;

import com.campus.module.device.service.DeviceService;
import com.campus.module.mqtt.dto.BindCodePayload;
import com.campus.module.mqtt.dto.MqttMessageEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BindCodeHandler implements MqttMessageHandler {

    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;

    @Override
    public boolean supports(String action) {
        return "bind".equals(action);
    }

    @Override
    public void handle(MqttMessageEnvelope envelope) {
        try {
            BindCodePayload payload = objectMapper.readValue(envelope.getPayload(), BindCodePayload.class);
            deviceService.registerBindCode(envelope.getDeviceId(), payload.getBindCode());
        } catch (Exception ex) {
            log.error("Handle bind failed, mqtt_msg_id={}", envelope.getMqttMsgId(), ex);
            throw new IllegalStateException(ex);
        }
    }
}
