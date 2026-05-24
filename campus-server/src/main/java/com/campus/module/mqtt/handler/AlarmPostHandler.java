package com.campus.module.mqtt.handler;

import com.campus.common.enums.FileStatus;
import com.campus.common.enums.PushStatus;
import com.campus.common.enums.ReadStatus;
import com.campus.common.util.IdGenerator;
import com.campus.module.device.entity.Device;
import com.campus.module.device.service.DeviceService;
import com.campus.module.event.entity.Event;
import com.campus.module.event.mapper.EventMapper;
import com.campus.module.event.vo.EventVO;
import com.campus.module.mqtt.dto.AlarmPostPayload;
import com.campus.module.mqtt.dto.MqttMessageEnvelope;
import com.campus.module.mqtt.publisher.MqttPublisher;
import com.campus.module.storage.dto.PresignedUploadInfo;
import com.campus.module.storage.service.StorageService;
import com.campus.module.websocket.handler.WebSocketPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmPostHandler implements MqttMessageHandler {

    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;
    private final StorageService storageService;
    private final EventMapper eventMapper;
    private final MqttPublisher mqttPublisher;
    private final WebSocketPushService webSocketPushService;

    @Override
    public boolean supports(String action) {
        return "alarm/post".equals(action);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(MqttMessageEnvelope envelope) {
        try {
            AlarmPostPayload payload = objectMapper.readValue(envelope.getPayload(), AlarmPostPayload.class);
            Device device = deviceService.findByDeviceId(envelope.getDeviceId());
            String eventId = IdGenerator.eventId(device.getDeviceId());
            PresignedUploadInfo uploadInfo = storageService.createUploadUrl(
                    device, eventId, payload.getFileName(), payload.getContentType());
            Event event = new Event();
            event.setMqttMsgId(payload.getMqttMsgId());
            event.setDeviceTableId(device.getId());
            event.setEventId(eventId);
            event.setEventType(payload.getEventType());
            event.setAlarmInfo(payload.getAlarmInfo());
            event.setFileKey(uploadInfo.getObjectKey());
            event.setFileStatus(FileStatus.UPLOADING.name());
            event.setPushStatus(PushStatus.PENDING.name());
            event.setReadStatus(ReadStatus.UNREAD.name());
            event.setEventTime(toEventTime(payload.getTimestamp()));
            eventMapper.insert(event);
            mqttPublisher.publishAlarmUpload(device.getProductType(), device.getDeviceId(), Map.of(
                    "mqtt_msg_id", payload.getMqttMsgId(),
                    "event_id", eventId,
                    "object_key", uploadInfo.getObjectKey(),
                    "upload_url", uploadInfo.getUploadUrl(),
                    "expire_seconds", uploadInfo.getExpireSeconds()
            ));
            if (webSocketPushService.pushNewEvent(toVO(event, device))) {
                event.setPushStatus(PushStatus.PUSHED.name());
                eventMapper.updateById(event);
            }
            log.info("Event created from MQTT, event_id={}, device_id={}", eventId, device.getDeviceId());
        } catch (Exception ex) {
            log.error("Handle alarm/post failed, mqtt_msg_id={}", envelope.getMqttMsgId(), ex);
            throw new IllegalStateException(ex);
        }
    }

    private LocalDateTime toEventTime(Long timestamp) {
        if (timestamp == null || timestamp <= 0) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("Asia/Shanghai"));
    }

    private EventVO toVO(Event event, Device device) {
        EventVO vo = new EventVO();
        vo.setEventId(event.getEventId());
        vo.setDeviceId(device.getDeviceId());
        vo.setEventType(event.getEventType());
        vo.setAlarmInfo(event.getAlarmInfo());
        vo.setFileStatus(event.getFileStatus());
        vo.setPushStatus(event.getPushStatus());
        vo.setReadStatus(event.getReadStatus());
        vo.setEventTime(event.getEventTime());
        return vo;
    }
}
