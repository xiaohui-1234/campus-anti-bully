package com.campus.module.device.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.constants.RedisKeys;
import com.campus.common.enums.DeviceOnlineStatus;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.config.CampusProperties;
import com.campus.module.device.dto.BindDeviceRequest;
import com.campus.module.device.dto.UpdateDeviceInfoRequest;
import com.campus.module.device.entity.Device;
import com.campus.module.device.entity.UserDeviceBind;
import com.campus.module.device.mapper.DeviceMapper;
import com.campus.module.device.mapper.UserDeviceBindMapper;
import com.campus.module.device.vo.DeviceVO;
import com.campus.module.websocket.session.WebSocketSessionManager;
import com.campus.security.LoginUser;
import com.campus.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final UserDeviceBindMapper bindMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CampusProperties properties;
    private final WebSocketSessionManager webSocketSessionManager;

    public PageResult<DeviceVO> listMine(long page, long size) {
        LoginUser loginUser = SecurityContextUtil.currentUser();
        Page<UserDeviceBind> bindPage = bindMapper.selectPage(new Page<>(safePage(page), safeSize(size)),
                new LambdaQueryWrapper<UserDeviceBind>()
                        .eq(UserDeviceBind::getUserTableId, loginUser.getUserTableId())
                        .orderByDesc(UserDeviceBind::getBindTime));
        List<DeviceVO> records = mapBindsToVO(bindPage.getRecords());
        return PageResult.of(bindPage, records);
    }

    public PageResult<DeviceVO> search(String keyword, String onlineStatus, long page, long size) {
        LoginUser loginUser = SecurityContextUtil.currentUser();
        List<UserDeviceBind> binds = bindMapper.selectList(new LambdaQueryWrapper<UserDeviceBind>()
                .eq(UserDeviceBind::getUserTableId, loginUser.getUserTableId())
                .orderByDesc(UserDeviceBind::getBindTime));
        List<DeviceVO> filtered = mapBindsToVO(binds).stream()
                .filter(vo -> matchesKeyword(vo, keyword))
                .filter(vo -> !StringUtils.hasText(onlineStatus) || onlineStatus.equalsIgnoreCase(vo.getOnlineStatus()))
                .sorted(Comparator.comparing(DeviceVO::getBindTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int from = (int) Math.min((safePage(page) - 1) * safeSize(size), filtered.size());
        int to = (int) Math.min(from + safeSize(size), filtered.size());
        return PageResult.of(filtered.subList(from, to), filtered.size(), safePage(page), safeSize(size));
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceVO bind(BindDeviceRequest request) {
        LoginUser loginUser = SecurityContextUtil.currentUser();
        Device device = findByDeviceId(request.getDeviceId());
        String bindCode = stringRedisTemplate.opsForValue().get(RedisKeys.deviceBindCode(request.getDeviceId()));
        if (!StringUtils.hasText(bindCode) || !Objects.equals(bindCode, request.getBindCode())) {
            throw new BizException(403, "绑定码错误或已过期");
        }
        UserDeviceBind existing = findBind(loginUser.getUserTableId(), device.getId());
        if (existing != null) {
            return toVO(device, existing);
        }
        UserDeviceBind bind = new UserDeviceBind();
        bind.setUserTableId(loginUser.getUserTableId());
        bind.setDeviceTableId(device.getId());
        bind.setDeviceName(request.getDeviceName());
        bind.setBindTime(LocalDateTime.now());
        bindMapper.insert(bind);
        log.info("Device bind success, user_id={}, device_id={}", loginUser.getUserId(), request.getDeviceId());
        return toVO(device, bind);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceVO updateInfo(String deviceId, UpdateDeviceInfoRequest request) {
        LoginUser loginUser = SecurityContextUtil.currentUser();
        Device device = findByDeviceId(deviceId);
        UserDeviceBind bind = ensureUserBoundDevice(loginUser.getUserTableId(), device.getId());
        bind.setDeviceName(request.getDeviceName());
        bind.setLocation(request.getLocation());
        bind.setNote(request.getNote());
        bindMapper.updateById(bind);
        return toVO(device, bind);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbind(String deviceId) {
        LoginUser loginUser = SecurityContextUtil.currentUser();
        Device device = findByDeviceId(deviceId);
        UserDeviceBind bind = ensureUserBoundDevice(loginUser.getUserTableId(), device.getId());
        bindMapper.deleteById(bind.getId());
        closeUserSessionsAfterCommit(loginUser.getUserId());
        log.info("Device unbind success, user_id={}, device_id={}", loginUser.getUserId(), deviceId);
    }

    public Device findByDeviceId(String deviceId) {
        Device device = deviceMapper.selectOne(new LambdaQueryWrapper<Device>().eq(Device::getDeviceId, deviceId));
        if (device == null) {
            throw BizException.notFound("设备不存在");
        }
        return device;
    }

    public void registerBindCode(String deviceId, String bindCode) {
        if (!StringUtils.hasText(bindCode)) {
            throw new BizException(400, "绑定码不能为空");
        }
        Device device = findByDeviceId(deviceId);
        stringRedisTemplate.opsForValue().set(
                RedisKeys.deviceBindCode(device.getDeviceId()),
                bindCode,
                properties.getCache().getBindCodeTtlSeconds(),
                TimeUnit.SECONDS);
        log.info("Device bind code refreshed, device_id={}, ttl_seconds={}",
                device.getDeviceId(), properties.getCache().getBindCodeTtlSeconds());
    }

    public UserDeviceBind ensureUserBoundDevice(Long userTableId, Long deviceTableId) {
        UserDeviceBind bind = findBind(userTableId, deviceTableId);
        if (bind == null) {
            log.warn("Permission denied, user_table_id={}, device_table_id={}", userTableId, deviceTableId);
            throw BizException.forbidden("无权访问该设备");
        }
        return bind;
    }

    public List<UserDeviceBind> listBinds(Long userTableId) {
        return bindMapper.selectList(new LambdaQueryWrapper<UserDeviceBind>()
                .eq(UserDeviceBind::getUserTableId, userTableId));
    }

    public Map<Long, Device> listDeviceMapByIds(List<Long> deviceTableIds) {
        if (deviceTableIds.isEmpty()) {
            return Map.of();
        }
        return deviceMapper.selectBatchIds(deviceTableIds).stream()
                .collect(Collectors.toMap(Device::getId, Function.identity()));
    }

    public List<UserDeviceBind> listBindsByDeviceId(Long deviceTableId) {
        return bindMapper.selectList(new LambdaQueryWrapper<UserDeviceBind>()
                .eq(UserDeviceBind::getDeviceTableId, deviceTableId));
    }

    public String onlineStatus(String deviceId) {
        Boolean exists = stringRedisTemplate.hasKey(RedisKeys.deviceOnline(deviceId));
        return Boolean.TRUE.equals(exists) ? DeviceOnlineStatus.ONLINE.name() : DeviceOnlineStatus.OFFLINE.name();
    }

    private UserDeviceBind findBind(Long userTableId, Long deviceTableId) {
        return bindMapper.selectOne(new LambdaQueryWrapper<UserDeviceBind>()
                .eq(UserDeviceBind::getUserTableId, userTableId)
                .eq(UserDeviceBind::getDeviceTableId, deviceTableId));
    }

    private List<DeviceVO> mapBindsToVO(List<UserDeviceBind> binds) {
        if (binds.isEmpty()) {
            return List.of();
        }
        List<Long> deviceIds = binds.stream().map(UserDeviceBind::getDeviceTableId).distinct().toList();
        Map<Long, Device> deviceMap = listDeviceMapByIds(deviceIds);
        List<DeviceVO> records = new ArrayList<>();
        for (UserDeviceBind bind : binds) {
            Device device = deviceMap.get(bind.getDeviceTableId());
            if (device != null) {
                records.add(toVO(device, bind));
            }
        }
        return records;
    }

    private DeviceVO toVO(Device device, UserDeviceBind bind) {
        DeviceVO vo = new DeviceVO();
        vo.setDeviceId(device.getDeviceId());
        vo.setProductType(device.getProductType());
        vo.setDeviceName(bind.getDeviceName());
        vo.setLocation(bind.getLocation());
        vo.setNote(bind.getNote());
        vo.setOnlineStatus(onlineStatus(device.getDeviceId()));
        vo.setLastOnlineTime(device.getLastOnlineTime());
        vo.setBindTime(bind.getBindTime());
        return vo;
    }

    private boolean matchesKeyword(DeviceVO vo, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String key = keyword.trim().toLowerCase();
        return contains(vo.getDeviceId(), key)
                || contains(vo.getDeviceName(), key)
                || contains(vo.getLocation(), key);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private void closeUserSessionsAfterCommit(String userId) {
        Runnable closeSessions = () -> webSocketSessionManager.closeUserSessions(userId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            closeSessions.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                closeSessions.run();
            }
        });
    }

    private long safePage(long page) {
        return Math.max(page, 1);
    }

    private long safeSize(long size) {
        return Math.min(Math.max(size, 1), properties.getEvent().getMaxPageSize());
    }
}
