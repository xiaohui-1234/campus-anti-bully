package com.campus.module.websocket.dto;

import lombok.Data;

import java.util.List;

@Data
public class WebSocketClientMessage {

    private String type;
    private List<String> deviceIds;
    private Long timestamp;
}
