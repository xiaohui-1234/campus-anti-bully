package com.campus.module.device.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateDeviceInfoRequest {

    @Size(max = 64)
    private String deviceName;

    @Size(max = 128)
    private String location;

    @Size(max = 255)
    private String note;
}
