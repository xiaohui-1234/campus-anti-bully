package com.campus.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxLoginRequest {

    @NotBlank
    private String code;
}
