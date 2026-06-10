package com.campus.module.admin.controller;

import com.campus.common.result.ApiResult;
import com.campus.common.result.PageResult;
import com.campus.module.admin.dto.CreateDeviceRequest;
import com.campus.module.admin.service.AdminDeviceService;
import com.campus.module.admin.vo.AdminDeviceVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/backend/v1/devices")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminDeviceController {

    private final AdminDeviceService adminDeviceService;

    @GetMapping
    public ApiResult<PageResult<AdminDeviceVO>> list(@RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size) {
        return ApiResult.success(adminDeviceService.list(keyword, page, size));
    }

    @PostMapping
    public ApiResult<AdminDeviceVO> create(@Valid @RequestBody CreateDeviceRequest request) {
        return ApiResult.success("设备新增成功", adminDeviceService.create(request));
    }

    @DeleteMapping("/{deviceId}")
    public ApiResult<Void> delete(
            @PathVariable
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "设备 ID 只能包含字母、数字、下划线和短横线")
            String deviceId) {
        adminDeviceService.delete(deviceId);
        return ApiResult.success("设备删除成功", null);
    }
}
