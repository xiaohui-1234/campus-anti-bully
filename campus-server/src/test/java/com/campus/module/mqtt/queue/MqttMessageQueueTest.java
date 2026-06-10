package com.campus.module.mqtt.queue;

import com.campus.module.mqtt.dto.MqttMessageEnvelope;
import com.campus.module.mqtt.handler.MqttMessageHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttMessageQueueTest {

    @Test
    void continuesConsumingAfterUnexpectedInterrupt() throws Exception {
        CountDownLatch handled = new CountDownLatch(1);
        MqttMessageHandler handler = new MqttMessageHandler() {
            @Override
            public boolean supports(String action) {
                return true;
            }

            @Override
            public void handle(MqttMessageEnvelope envelope) {
                handled.countDown();
            }
        };
        MqttMessageQueue queue = new MqttMessageQueue(List.of(handler));

        queue.start();
        Thread worker = (Thread) ReflectionTestUtils.getField(queue, "worker");
        try {
            assertTrue(waitForState(worker, Thread.State.WAITING, Duration.ofSeconds(2)));
            worker.interrupt();
            queue.offer(new MqttMessageEnvelope("topic", "{}", "product", "device", "action", "message"));

            assertTrue(handled.await(2, TimeUnit.SECONDS));
            assertTrue(worker.isAlive());
        } finally {
            queue.stop();
            worker.join(2000);
        }
    }

    private boolean waitForState(Thread thread, Thread.State state, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (thread.getState() == state) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }
}
