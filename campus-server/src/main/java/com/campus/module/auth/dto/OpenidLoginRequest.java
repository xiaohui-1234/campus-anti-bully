package com.campus.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OpenidLoginRequest {

    @NotBlank
    private String openid;
}
