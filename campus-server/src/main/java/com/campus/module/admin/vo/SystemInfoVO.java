package com.campus.module.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemInfoVO {

    private String applicationName;
    private String javaVersion;
    private String osName;
    private long deviceCount;
    private long userCount;
    private long eventCount;
}
