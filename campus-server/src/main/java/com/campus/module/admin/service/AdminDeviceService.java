package com.campus.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.constants.RedisKeys;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.common.util.HashUtil;
import com.campus.config.CampusProperties;
import com.campus.module.admin.dto.CreateDeviceRequest;
import com.campus.module.admin.vo.AdminDeviceVO;
import com.campus.module.device.entity.Device;
import com.campus.module.device.entity.DeviceConfigWifi;
import com.campus.module.device.entity.UserDeviceBind;
import com.campus.module.device.mapper.DeviceConfigWifiMapper;
import com.campus.module.device.mapper.DeviceMapper;
import com.campus.module.device.mapper.UserDeviceBindMapper;
import com.campus.module.device.service.DeviceService;
import com.campus.module.event.entity.Event;
import com.campus.module.event.mapper.EventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDeviceService {

    private final DeviceMapper deviceMapper;
    private final UserDeviceBindMapper bindMapper;
    private final DeviceConfigWifiMapper wifiMapper;
    private final EventMapper eventMapper;
    private final DeviceService deviceService;
    private final StringRedisTemplate stringRedisTemplate;
    private final CampusProperties properties;

    public PageResult<AdminDeviceVO> list(String keyword, long page, long size) {
        LambdaQueryWrapper<Device> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String key = keyword.trim();
            query.and(wrapper -> wrapper
                    .like(Device::getDeviceId, key)
                    .or()
                    .like(Device::getProductType, key));
        }
        query.orderByDesc(Device::getCreateTime).orderByDesc(Device::getId);

        Page<Device> devicePage = deviceMapper.selectPage(
                new Page<>(safePage(page), safeSize(size)),
                query);
        List<AdminDeviceVO> records = devicePage.getRecords().stream()
                .map(this::toVO)
                .toList();
        return PageResult.of(devicePage, records);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminDeviceVO create(CreateDeviceRequest request) {
        String deviceId = request.getDeviceId().trim();
        String productType = request.getProductType().trim();
        Long existingCount = deviceMapper.selectCount(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceId, deviceId));
        if (existingCount > 0) {
            throw BizException.conflict("设备 ID 已存在");
        }

        Device device = new Device();
        device.setDeviceId(deviceId);
        device.setProductType(productType);
        // 设备密钥只在创建时接收，入库前立即哈希，禁止保存或返回明文。
        device.setDeviceSecret(HashUtil.sha256(request.getDeviceSecret()));
        device.setCreateTime(LocalDateTime.now());
        deviceMapper.insert(device);
        return toVO(device);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String deviceId) {
        Device device = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceId, deviceId));
        if (device == null) {
            throw BizException.notFound("设备不存在");
        }

        long bindCount = bindMapper.selectCount(new LambdaQueryWrapper<UserDeviceBind>()
                .eq(UserDeviceBind::getDeviceTableId, device.getId()));
        long eventCount = eventMapper.selectCount(new LambdaQueryWrapper<Event>()
                .eq(Event::getDeviceTableId, device.getId()));
        long wifiCount = wifiMapper.selectCount(new LambdaQueryWrapper<DeviceConfigWifi>()
                .eq(DeviceConfigWifi::getDeviceTableId, device.getId()));
        if (bindCount > 0 || eventCount > 0 || wifiCount > 0) {
            throw BizException.conflict(String.format(
                    "设备存在关联数据，无法删除（用户绑定 %d 条、事件 %d 条、WiFi 配置 %d 条）",
                    bindCount,
                    eventCount,
                    wifiCount));
        }

        try {
            deviceMapper.deleteById(device.getId());
        } catch (DataIntegrityViolationException ex) {
            // 关联检查后仍可能有并发写入，统一转换为明确的业务冲突提示。
            throw BizException.conflict("设备存在关联数据，无法删除，请刷新后重试");
        }
        stringRedisTemplate.delete(List.of(
                RedisKeys.deviceOnline(deviceId),
                RedisKeys.deviceLastHeartbeat(deviceId),
                RedisKeys.deviceBindCode(deviceId)));
    }

    private AdminDeviceVO toVO(Device device) {
        return AdminDeviceVO.builder()
                .deviceId(device.getDeviceId())
                .productType(device.getProductType())
                .onlineStatus(deviceService.onlineStatus(device.getDeviceId()))
                .createTime(device.getCreateTime())
                .lastOnlineTime(device.getLastOnlineTime())
                .build();
    }

    private long safePage(long page) {
        return Math.max(page, 1);
    }

    private long safeSize(long size) {
        return Math.min(Math.max(size, 1), properties.getEvent().getMaxPageSize());
    }
}
