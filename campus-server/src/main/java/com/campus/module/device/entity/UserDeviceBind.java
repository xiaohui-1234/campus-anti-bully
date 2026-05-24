package com.campus.module.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_device_bind")
public class UserDeviceBind {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceTableId;
    private Long userTableId;
    private LocalDateTime bindTime;
    private String deviceName;
    private String location;
    private String note;
}
