package com.campus.module.event.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class RefreshUrlRequest {

    @Min(60)
    @Max(86400)
    private Integer expireSeconds = 3600;
}
