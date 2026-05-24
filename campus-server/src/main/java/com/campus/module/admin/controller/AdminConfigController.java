package com.campus.module.admin.controller;

import com.campus.common.result.ApiResult;
import com.campus.module.admin.dto.ConfigUpdateRequest;
import com.campus.module.admin.service.AdminConfigService;
import com.campus.module.admin.service.AdminSystemService;
import com.campus.module.admin.vo.ConfigVO;
import com.campus.module.admin.vo.SystemInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/backend/v1")
@PreAuthorize("hasRole('ADMIN')")
public class AdminConfigController {

    private final AdminConfigService adminConfigService;
    private final AdminSystemService adminSystemService;

    @GetMapping("/config")
    public ApiResult<ConfigVO> getConfig() {
        return ApiResult.success(adminConfigService.getConfig());
    }

    @PutMapping("/config")
    public ApiResult<ConfigVO> updateConfig(@RequestBody ConfigUpdateRequest request) {
        return ApiResult.success("配置已更新，部分基础设施连接参数需重启服务生效", adminConfigService.update(request));
    }

    @GetMapping("/system/info")
    public ApiResult<SystemInfoVO> systemInfo() {
        return ApiResult.success(adminSystemService.info());
    }
}
