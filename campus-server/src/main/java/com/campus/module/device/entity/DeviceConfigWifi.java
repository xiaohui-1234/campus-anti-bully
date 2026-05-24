package com.campus.module.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_config_wifi")
public class DeviceConfigWifi {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceTableId;
    private Integer configId;
    private String wifiName;
    private LocalDateTime setTime;
}
