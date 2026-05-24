package com.campus.module.event.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event")
public class Event {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String mqttMsgId;
    private Long deviceTableId;
    private String eventId;
    private String eventType;
    private String alarmInfo;
    private String fileKey;
    private String fileStatus;
    private String pushStatus;
    private String readStatus;
    private LocalDateTime eventTime;
}
