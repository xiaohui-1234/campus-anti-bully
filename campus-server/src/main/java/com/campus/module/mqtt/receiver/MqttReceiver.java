package com.campus.module.mqtt.receiver;

import com.campus.config.CampusProperties;
import com.campus.module.mqtt.router.MqttRouter;
import com.campus.module.mqtt.topic.MqttTopicBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttReceiver implements MqttCallback {

    private final CampusProperties properties;
    private final MqttClient mqttClient;
    private final MqttRouter router;
    private final MqttTopicBuilder topicBuilder;

    @PostConstruct
    public void start() {
        if (!properties.getMqtt().isEnabled()) {
            log.info("MQTT receiver disabled by configuration");
            return;
        }
        try {
            mqttClient.setCallback(this);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setUserName(properties.getMqtt().getUsername());
            options.setPassword(properties.getMqtt().getPassword().toCharArray());
            mqttClient.connect(options);
            subscribe(topicBuilder.subscription("alarm/post"));
            subscribe(topicBuilder.subscription("alarm/confirm"));
            subscribe(topicBuilder.subscription("bind"));
            subscribe(topicBuilder.subscription("status/online"));
            subscribe(topicBuilder.subscription("config/wifi/set/reply"));
            subscribe(topicBuilder.subscription("config/wifi/list"));
            log.info("MQTT receiver connected, broker={}", properties.getMqtt().getBrokerUrl());
        } catch (Exception ex) {
            log.warn("MQTT receiver start failed, application will continue", ex);
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnectForcibly(1000L, properties.getMqtt().getCompletionTimeoutMs());
            }
            mqttClient.close();
        } catch (Exception ex) {
            log.warn("MQTT client shutdown failed", ex);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost", cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.info("MQTT message received, topic={}", topic);
        router.route(topic, payload);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    private void subscribe(String topic) throws Exception {
        mqttClient.subscribe(topic, properties.getMqtt().getQos());
    }
}
