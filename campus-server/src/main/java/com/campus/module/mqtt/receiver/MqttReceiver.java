package com.campus.module.mqtt.receiver;

import com.campus.config.CampusProperties;
import com.campus.module.mqtt.router.MqttRouter;
import com.campus.module.mqtt.topic.MqttTopicBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttReceiver implements MqttCallbackExtended {

    private static final long CONNECT_RETRY_DELAY_SECONDS = 10;
    private static final long RESUBSCRIBE_RETRY_DELAY_SECONDS = 5;
    private static final long SUBSCRIPTION_REFRESH_SECONDS = 60;
    private static final String REASON_STARTUP = "startup";
    private static final String REASON_RECONNECT = "reconnect";
    private static final String REASON_HEALTH_CHECK = "health_check";

    private static final List<String> SUBSCRIBE_ACTIONS = List.of(
            "alarm/post",
            "alarm/confirm",
            "bind",
            "status/online",
            "config/wifi/set/reply",
            "config/wifi/list"
    );

    private final CampusProperties properties;
    private final MqttClient mqttClient;
    private final MqttRouter router;
    private final MqttTopicBuilder topicBuilder;
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connectRetryScheduled = new AtomicBoolean(false);
    private final AtomicBoolean resubscribeRetryScheduled = new AtomicBoolean(false);
    private final AtomicBoolean subscriptionRefreshStarted = new AtomicBoolean(false);
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "mqtt-receiver-retry");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    public void start() {
        if (!properties.getMqtt().isEnabled()) {
            log.info("MQTT receiver disabled by configuration");
            return;
        }
        running.set(true);
        mqttClient.setCallback(this);
        connectAndSubscribe(REASON_STARTUP);
        startSubscriptionRefresh();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        retryExecutor.shutdownNow();
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
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT connection complete, reconnect={}, server_uri={}", reconnect, serverURI);
        if (!running.get()) {
            return;
        }
        if (!reconnect) {
            return;
        }
        if (resubscribeSafely(REASON_RECONNECT)) {
            log.info("MQTT receiver reconnected and resubscribed, broker={}", serverURI);
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

    private void connectAndSubscribe(String reason) {
        if (!running.get()) {
            return;
        }
        if (!lifecycleLock.tryLock()) {
            scheduleConnectRetry(reason);
            return;
        }
        try {
            if (!mqttClient.isConnected()) {
                mqttClient.connect(buildConnectOptions());
                log.info("MQTT receiver connected, broker={}, reason={}", properties.getMqtt().getBrokerUrl(), reason);
            }
            subscribeAll(reason);
        } catch (Exception ex) {
            log.warn("MQTT connect or subscribe failed, reason={}, retry will be scheduled", reason, ex);
            scheduleConnectRetry(reason);
        } finally {
            lifecycleLock.unlock();
        }
    }

    private MqttConnectOptions buildConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        if (properties.getMqtt().getUsername() != null) {
            options.setUserName(properties.getMqtt().getUsername());
        }
        if (properties.getMqtt().getPassword() != null) {
            options.setPassword(properties.getMqtt().getPassword().toCharArray());
        }
        return options;
    }

    private void startSubscriptionRefresh() {
        if (!subscriptionRefreshStarted.compareAndSet(false, true)) {
            return;
        }
        try {
            retryExecutor.scheduleWithFixedDelay(
                    this::refreshSubscriptionsSafely,
                    SUBSCRIPTION_REFRESH_SECONDS,
                    SUBSCRIPTION_REFRESH_SECONDS,
                    TimeUnit.SECONDS);
        } catch (RejectedExecutionException ex) {
            log.warn("MQTT subscription refresh scheduler rejected", ex);
        }
    }

    private void refreshSubscriptionsSafely() {
        if (!running.get()) {
            return;
        }
        if (!mqttClient.isConnected()) {
            log.warn("MQTT health check found disconnected client, scheduling reconnect");
            scheduleConnectRetry(REASON_HEALTH_CHECK);
            return;
        }
        resubscribeSafely(REASON_HEALTH_CHECK);
    }

    private void scheduleConnectRetry(String reason) {
        if (!running.get() || !connectRetryScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            retryExecutor.schedule(() -> {
                connectRetryScheduled.set(false);
                connectAndSubscribe("retry:" + reason);
            }, CONNECT_RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
            log.warn("MQTT connect retry scheduled, reason={}, delay_seconds={}", reason, CONNECT_RETRY_DELAY_SECONDS);
        } catch (RejectedExecutionException ex) {
            connectRetryScheduled.set(false);
            log.warn("MQTT connect retry scheduler rejected, reason={}", reason, ex);
        }
    }

    private void scheduleResubscribeRetry(String reason) {
        if (!running.get() || !resubscribeRetryScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            retryExecutor.schedule(() -> {
                resubscribeRetryScheduled.set(false);
                resubscribeSafely("retry:" + reason);
            }, RESUBSCRIBE_RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
            log.warn("MQTT resubscribe retry scheduled, reason={}, delay_seconds={}",
                    reason, RESUBSCRIBE_RETRY_DELAY_SECONDS);
        } catch (RejectedExecutionException ex) {
            resubscribeRetryScheduled.set(false);
            log.warn("MQTT resubscribe retry scheduler rejected, reason={}", reason, ex);
        }
    }

    private boolean resubscribeSafely(String reason) {
        if (!running.get()) {
            return false;
        }
        if (!mqttClient.isConnected()) {
            scheduleConnectRetry(reason);
            return false;
        }
        if (!lifecycleLock.tryLock()) {
            scheduleResubscribeRetry(reason);
            return false;
        }
        try {
            subscribeAll(reason);
            return true;
        } catch (Exception ex) {
            log.warn("MQTT resubscribe failed, reason={}", reason, ex);
            scheduleResubscribeRetry(reason);
            return false;
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void subscribeAll(String reason) throws Exception {
        int qos = properties.getMqtt().getQos();
        for (String action : SUBSCRIBE_ACTIONS) {
            subscribe(topicBuilder.subscription(action), qos, reason);
        }
        log.info("MQTT subscribe all completed, reason={}, count={}, qos={}",
                reason, SUBSCRIBE_ACTIONS.size(), qos);
    }

    private void subscribe(String topic, int qos, String reason) throws Exception {
        mqttClient.subscribe(topic, qos);
        if (REASON_HEALTH_CHECK.equals(reason)) {
            log.debug("MQTT subscribed, topic={}, qos={}, reason={}", topic, qos, reason);
        } else {
            log.info("MQTT subscribed, topic={}, qos={}, reason={}", topic, qos, reason);
        }
    }
}
