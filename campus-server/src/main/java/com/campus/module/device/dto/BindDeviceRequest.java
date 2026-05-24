package com.campus.module.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BindDeviceRequest {

    @NotBlank
    @Size(max = 64)
    private String deviceId;

    @NotBlank
    @Size(max = 16)
    private String bindCode;

    @Size(max = 64)
    private String deviceName;
}
