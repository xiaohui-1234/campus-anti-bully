package com.campus.module.device.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceVO {

    private String deviceId;
    private String productType;
    private String deviceName;
    private String location;
    private String note;
    private String onlineStatus;
    private LocalDateTime lastOnlineTime;
    private LocalDateTime bindTime;
}
