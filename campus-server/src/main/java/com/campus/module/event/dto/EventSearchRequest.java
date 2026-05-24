package com.campus.module.event.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class EventSearchRequest {

    private String deviceId;
    private Long page = 1L;
    private Long size = 10L;
    private String eventType;
    private String fileStatus;
    private String pushStatus;
    private String readStatus;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    private String keyword;
}
