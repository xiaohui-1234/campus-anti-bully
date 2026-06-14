package com.campus.module.mqtt.receiver;

import com.campus.config.CampusProperties;
import com.campus.module.mqtt.router.MqttRouter;
import com.campus.module.mqtt.topic.MqttTopicBuilder;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttReceiverTest {

    @Test
    void stopDisconnectsConnectedClientBeforeClose() throws Exception {
        CampusProperties properties = new CampusProperties();
        properties.getMqtt().setCompletionTimeoutMs(1234);
        MqttClient mqttClient = mock(MqttClient.class);
        when(mqttClient.isConnected()).thenReturn(true);
        MqttReceiver receiver = new MqttReceiver(
                properties,
                mqttClient,
                mock(MqttRouter.class),
                mock(MqttTopicBuilder.class));

        receiver.stop();

        InOrder order = inOrder(mqttClient);
        order.verify(mqttClient).disconnectForcibly(1000L, 1234L);
        order.verify(mqttClient).close();
    }

    @Test
    void stopClosesDisconnectedClient() throws Exception {
        CampusProperties properties = new CampusProperties();
        MqttClient mqttClient = mock(MqttClient.class);
        when(mqttClient.isConnected()).thenReturn(false);
        MqttReceiver receiver = new MqttReceiver(
                properties,
                mqttClient,
                mock(MqttRouter.class),
                mock(MqttTopicBuilder.class));

        receiver.stop();

        verify(mqttClient).close();
    }
}
