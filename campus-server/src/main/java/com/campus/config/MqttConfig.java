package com.campus.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttConfig {

    @Bean
    public MqttClient mqttClient(CampusProperties properties) throws Exception {
        CampusProperties.Mqtt mqtt = properties.getMqtt();
        return new MqttClient(mqtt.getBrokerUrl(), mqtt.getClientId(), new MemoryPersistence());
    }
}
