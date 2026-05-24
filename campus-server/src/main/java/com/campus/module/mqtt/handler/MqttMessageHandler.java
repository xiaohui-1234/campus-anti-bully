package com.campus.module.mqtt.handler;

import com.campus.module.mqtt.dto.MqttMessageEnvelope;

public interface MqttMessageHandler {

    boolean supports(String action);

    void handle(MqttMessageEnvelope envelope);
}
