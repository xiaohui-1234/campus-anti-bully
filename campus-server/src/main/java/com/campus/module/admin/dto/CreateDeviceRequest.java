package com.campus.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDeviceRequest {

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "只能包含字母、数字、下划线和短横线")
    private String deviceId;

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "只能包含字母、数字、下划线和短横线")
    private String productType;

    @NotBlank
    @Size(max = 255)
    private String deviceSecret;
}
