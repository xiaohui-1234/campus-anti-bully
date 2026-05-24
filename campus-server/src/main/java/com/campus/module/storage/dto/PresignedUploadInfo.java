package com.campus.module.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadInfo {

    private String eventId;
    private String objectKey;
    private String uploadUrl;
    private int expireSeconds;
}
