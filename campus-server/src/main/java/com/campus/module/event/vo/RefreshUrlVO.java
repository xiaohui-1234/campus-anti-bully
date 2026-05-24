package com.campus.module.event.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RefreshUrlVO {

    private String eventId;
    private String fileUrl;
    private int expireSeconds;
    private LocalDateTime expireAt;
}
