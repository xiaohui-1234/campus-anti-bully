package com.campus.module.mqtt.receiver;

import com.campus.config.CampusProperties;
import com.campus.module.mqtt.router.MqttRouter;
import com.campus.module.mqtt.topic.MqttTopicBuilder;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttReceiverTest {

    private CampusProperties properties;
    private MqttClient mqttClient;
    private MqttReceiver receiver;

    @BeforeEach
    void setUp() {
        properties = new CampusProperties();
        properties.getMqtt().setEnabled(true);
        properties.getMqtt().setUsername("user");
        properties.getMqtt().setPassword("password");
        properties.getMqtt().setQos(1);
        mqttClient = mock(MqttClient.class);
        receiver = new MqttReceiver(
                properties,
                mqttClient,
                mock(MqttRouter.class),
                new MqttTopicBuilder());
    }

    @AfterEach
    void tearDown() {
        receiver.stop();
    }

    @Test
    void startConnectsWithAutoReconnectAndSubscribesAllTopics() throws Exception {
        receiver.start();

        ArgumentCaptor<MqttConnectOptions> optionsCaptor = ArgumentCaptor.forClass(MqttConnectOptions.class);
        verify(mqttClient).setCallback(receiver);
        verify(mqttClient).connect(optionsCaptor.capture());
        assertTrue(optionsCaptor.getValue().isAutomaticReconnect());
        assertTrue(optionsCaptor.getValue().isCleanSession());
        verifySubscriptions();
    }

    @Test
    void connectCompleteResubscribesAfterReconnect() throws Exception {
        receiver.start();
        clearInvocations(mqttClient);
        when(mqttClient.isConnected()).thenReturn(true);

        receiver.connectComplete(true, "tcp://broker:1883");

        verifySubscriptions();
    }

    @Test
    void connectCompleteDoesNotResubscribeInitialConnection() throws Exception {
        receiver.start();
        clearInvocations(mqttClient);

        receiver.connectComplete(false, "tcp://broker:1883");

        verify(mqttClient, never()).subscribe(any(String.class), anyInt());
    }

    @Test
    void stopDisconnectsConnectedClientBeforeClose() throws Exception {
        properties.getMqtt().setCompletionTimeoutMs(1234);
        when(mqttClient.isConnected()).thenReturn(true);

        receiver.stop();

        InOrder order = inOrder(mqttClient);
        order.verify(mqttClient).disconnectForcibly(1000L, 1234L);
        order.verify(mqttClient).close();
    }

    @Test
    void stopClosesDisconnectedClient() throws Exception {
        when(mqttClient.isConnected()).thenReturn(false);

        receiver.stop();

        verify(mqttClient).close();
    }

    private void verifySubscriptions() throws Exception {
        verify(mqttClient).subscribe(eq("device/+/+/alarm/post"), eq(1));
        verify(mqttClient).subscribe(eq("device/+/+/alarm/confirm"), eq(1));
        verify(mqttClient).subscribe(eq("device/+/+/bind"), eq(1));
        verify(mqttClient).subscribe(eq("device/+/+/status/online"), eq(1));
        verify(mqttClient).subscribe(eq("device/+/+/config/wifi/set/reply"), eq(1));
        verify(mqttClient).subscribe(eq("device/+/+/config/wifi/list"), eq(1));
    }
}
