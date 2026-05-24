package com.campus.module.mqtt.queue;

import com.campus.module.mqtt.dto.MqttMessageEnvelope;
import com.campus.module.mqtt.handler.MqttMessageHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageQueue {

    private final List<MqttMessageHandler> handlers;
    private final BlockingQueue<MqttMessageEnvelope> queue = new LinkedBlockingQueue<>(10000);
    private volatile boolean running = true;
    private Thread worker;

    public void offer(MqttMessageEnvelope envelope) {
        if (!queue.offer(envelope)) {
            log.error("MQTT queue full, topic={}, mqtt_msg_id={}", envelope.getTopic(), envelope.getMqttMsgId());
        }
    }

    @PostConstruct
    public void start() {
        worker = new Thread(this::consume, "mqtt-message-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void consume() {
        while (running) {
            try {
                MqttMessageEnvelope envelope = queue.take();
                handlers.stream()
                        .filter(handler -> handler.supports(envelope.getAction()))
                        .findFirst()
                        .ifPresentOrElse(handler -> handler.handle(envelope),
                                () -> log.warn("No MQTT handler for action={}", envelope.getAction()));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.error("MQTT message handle failed", ex);
            }
        }
    }
}
