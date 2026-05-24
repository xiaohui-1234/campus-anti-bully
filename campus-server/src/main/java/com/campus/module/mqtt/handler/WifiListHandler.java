package com.campus.module.mqtt.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.module.device.entity.Device;
import com.campus.module.device.entity.DeviceConfigWifi;
import com.campus.module.device.mapper.DeviceConfigWifiMapper;
import com.campus.module.device.service.DeviceService;
import com.campus.module.mqtt.dto.MqttMessageEnvelope;
import com.campus.module.mqtt.dto.WifiListPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class WifiListHandler implements MqttMessageHandler {

    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;
    private final DeviceConfigWifiMapper wifiMapper;

    @Override
    public boolean supports(String action) {
        return "config/wifi/list".equals(action);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(MqttMessageEnvelope envelope) {
        try {
            WifiListPayload payload = objectMapper.readValue(envelope.getPayload(), WifiListPayload.class);
            Device device = deviceService.findByDeviceId(envelope.getDeviceId());
            if (payload.getConfig() == null) {
                return;
            }
            for (WifiListPayload.WifiConfig config : payload.getConfig()) {
                DeviceConfigWifi wifi = wifiMapper.selectOne(new LambdaQueryWrapper<DeviceConfigWifi>()
                        .eq(DeviceConfigWifi::getDeviceTableId, device.getId())
                        .eq(DeviceConfigWifi::getConfigId, config.getConfigId()));
                if (wifi == null) {
                    wifi = new DeviceConfigWifi();
                    wifi.setDeviceTableId(device.getId());
                    wifi.setConfigId(config.getConfigId());
                }
                wifi.setWifiName(config.getWifiName());
                wifi.setSetTime(LocalDateTime.now());
                if (wifi.getId() == null) {
                    wifiMapper.insert(wifi);
                } else {
                    wifiMapper.updateById(wifi);
                }
            }
            log.info("WiFi list updated, device_id={}, count={}", device.getDeviceId(), payload.getConfig().size());
        } catch (Exception ex) {
            log.error("Handle wifi list failed, mqtt_msg_id={}", envelope.getMqttMsgId(), ex);
            throw new IllegalStateException(ex);
        }
    }
}
