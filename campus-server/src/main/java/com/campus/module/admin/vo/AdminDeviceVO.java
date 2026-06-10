package com.campus.module.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminDeviceVO {

    private String deviceId;
    private String productType;
    private String onlineStatus;
    private LocalDateTime createTime;
    private LocalDateTime lastOnlineTime;
}
