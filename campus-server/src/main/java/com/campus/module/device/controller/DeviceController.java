package com.campus.module.device.controller;

import com.campus.common.result.ApiResult;
import com.campus.common.result.PageResult;
import com.campus.module.device.dto.BindDeviceRequest;
import com.campus.module.device.dto.UpdateDeviceInfoRequest;
import com.campus.module.device.service.DeviceService;
import com.campus.module.device.vo.DeviceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public ApiResult<PageResult<DeviceVO>> list(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        return ApiResult.success(deviceService.listMine(page, size));
    }

    @PostMapping("/bind")
    public ApiResult<DeviceVO> bind(@Valid @RequestBody BindDeviceRequest request) {
        return ApiResult.success("绑定成功", deviceService.bind(request));
    }

    @GetMapping("/search")
    public ApiResult<PageResult<DeviceVO>> search(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String onlineStatus,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        return ApiResult.success(deviceService.search(keyword, onlineStatus, page, size));
    }

    @PutMapping("/{deviceId}/info")
    public ApiResult<DeviceVO> updateInfo(@PathVariable String deviceId,
                                          @Valid @RequestBody UpdateDeviceInfoRequest request) {
        return ApiResult.success("保存成功", deviceService.updateInfo(deviceId, request));
    }

    @DeleteMapping("/{deviceId}/binding")
    public ApiResult<Void> unbind(@PathVariable String deviceId) {
        deviceService.unbind(deviceId);
        return ApiResult.success("解绑成功", null);
    }
}
