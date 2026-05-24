package com.campus.module.event.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventVO {

    private String eventId;
    private String deviceId;
    private String eventType;
    private String alarmInfo;
    private String fileStatus;
    private String fileUrl = "";
    private String pushStatus;
    private String readStatus;
    private LocalDateTime eventTime;
}
