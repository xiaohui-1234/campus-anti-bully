package com.campus.module.mqtt.handler;

import com.campus.common.enums.FileStatus;
import com.campus.common.enums.PushStatus;
import com.campus.module.device.entity.Device;
import com.campus.module.device.service.DeviceService;
import com.campus.module.event.entity.Event;
import com.campus.module.event.mapper.EventMapper;
import com.campus.module.event.service.EventService;
import com.campus.module.event.vo.EventVO;
import com.campus.module.mqtt.dto.AlarmConfirmPayload;
import com.campus.module.mqtt.dto.MqttMessageEnvelope;
import com.campus.module.websocket.handler.WebSocketPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmConfirmHandler implements MqttMessageHandler {

    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;
    private final EventService eventService;
    private final EventMapper eventMapper;
    private final WebSocketPushService webSocketPushService;

    @Override
    public boolean supports(String action) {
        return "alarm/confirm".equals(action);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(MqttMessageEnvelope envelope) {
        try {
            AlarmConfirmPayload payload = objectMapper.readValue(envelope.getPayload(), AlarmConfirmPayload.class);
            Device device = deviceService.findByDeviceId(envelope.getDeviceId());
            Event event = eventService.findByEventId(payload.getEventId());
            if (!event.getDeviceTableId().equals(device.getId())) {
                log.warn("Alarm confirm ignored because device mismatch, event_id={}", payload.getEventId());
                return;
            }
            if (payload.getObjectKey() != null && !payload.getObjectKey().equals(event.getFileKey())) {
                log.warn("Alarm confirm object key mismatch, event_id={}", payload.getEventId());
                return;
            }
            event.setFileStatus("success".equalsIgnoreCase(payload.getUploadStatus())
                    ? FileStatus.SUCCESS.name()
                    : FileStatus.FAILED.name());
            event.setPushStatus(PushStatus.PUSHED.name());
            EventVO vo = eventService.toVO(event, device);
            boolean pushed = webSocketPushService.pushNewEvent(vo);
            event.setPushStatus(pushed ? PushStatus.PUSHED.name() : PushStatus.PENDING.name());
            eventMapper.updateById(event);
            log.info("Alarm confirm handled, event_id={}, pushed={}", event.getEventId(), pushed);
        } catch (Exception ex) {
            log.error("Handle alarm/confirm failed, mqtt_msg_id={}", envelope.getMqttMsgId(), ex);
            throw new IllegalStateException(ex);
        }
    }
}
