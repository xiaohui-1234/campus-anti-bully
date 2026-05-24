package com.campus.module.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresignedAccessInfo {

    private String fileUrl;
    private int expireSeconds;
    private LocalDateTime expireAt;
}
