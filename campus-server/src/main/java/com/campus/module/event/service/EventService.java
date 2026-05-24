package com.campus.module.event.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.enums.FileStatus;
import com.campus.common.enums.PushStatus;
import com.campus.common.enums.ReadStatus;
import com.campus.common.exception.BizException;
import com.campus.common.result.PageResult;
import com.campus.config.CampusProperties;
import com.campus.module.device.entity.Device;
import com.campus.module.device.entity.UserDeviceBind;
import com.campus.module.device.service.DeviceService;
import com.campus.module.event.dto.EventSearchRequest;
import com.campus.module.event.dto.RefreshUrlRequest;
import com.campus.module.event.entity.Event;
import com.campus.module.event.mapper.EventMapper;
import com.campus.module.event.vo.EventVO;
import com.campus.module.event.vo.RefreshUrlVO;
import com.campus.module.storage.dto.PresignedAccessInfo;
import com.campus.module.storage.service.StorageService;
import com.campus.security.LoginUser;
import com.campus.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventMapper eventMapper;
    private final DeviceService deviceService;
    private final StorageService storageService;
    private final CampusProperties properties;

    @Transactional(rollbackFor = Exception.class)
    public List<EventVO> unpulled() {
        expireStaleUploads();
        LoginUser loginUser = SecurityContextUtil.currentUser();
        BoundDevices bound = boundDevices(loginUser);
        if (bound.deviceTableIds().isEmpty()) {
            return List.of();
        }
        List<Event> events = eventMapper.selectList(new LambdaQueryWrapper<Event>()
                .in(Event::getDeviceTableId, bound.deviceTableIds())
                .eq(Event::getPushStatus, PushStatus.PENDING.name())
                .orderByDesc(Event::getEventTime));
        for (Event event : events) {
            event.setPushStatus(PushStatus.PUSHED.name());
            eventMapper.updateById(event);
        }
        return events.stream().map(event -> toVO(event, bound.deviceMap().get(event.getDeviceTableId()))).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRead(String eventId) {
        LoginUser loginUser = SecurityContextUtil.currentUser();
        Event event = findByEventId(eventId);
        deviceService.ensureUserBoundDevice(loginUser.getUserTableId(), event.getDeviceTableId());
        event.setReadStatus(ReadStatus.READ.name());
        eventMapper.updateById(event);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String eventId) {
        LoginUser loginUser = SecurityContextUtil.currentUser();
        Event event = findByEventId(eventId);
        deviceService.ensureUserBoundDevice(loginUser.getUserTableId(), event.getDeviceTableId());
        eventMapper.deleteById(event.getId());
    }

    public PageResult<EventVO> search(EventSearchRequest request) {
        expireStaleUploads();
        LoginUser loginUser = SecurityContextUtil.currentUser();
        BoundDevices bound = boundDevices(loginUser);
        if (bound.deviceTableIds().isEmpty()) {
            return PageResult.of(List.of(), 0, request.getPage(), request.getSize());
        }
        List<Long> allowedDeviceIds = bound.deviceTableIds();
        if (StringUtils.hasText(request.getDeviceId())) {
            Device target = deviceService.findByDeviceId(request.getDeviceId());
            deviceService.ensureUserBoundDevice(loginUser.getUserTableId(), target.getId());
            allowedDeviceIds = List.of(target.getId());
        }
        LambdaQueryWrapper<Event> wrapper = new LambdaQueryWrapper<Event>()
                .in(Event::getDeviceTableId, allowedDeviceIds)
                .eq(StringUtils.hasText(request.getEventType()), Event::getEventType, request.getEventType())
                .eq(StringUtils.hasText(request.getFileStatus()), Event::getFileStatus, request.getFileStatus())
                .eq(StringUtils.hasText(request.getPushStatus()), Event::getPushStatus, request.getPushStatus())
                .eq(StringUtils.hasText(request.getReadStatus()), Event::getReadStatus, request.getReadStatus())
                .ge(request.getStartTime() != null, Event::getEventTime, request.getStartTime())
                .le(request.getEndTime() != null, Event::getEventTime, request.getEndTime())
                .like(StringUtils.hasText(request.getKeyword()), Event::getAlarmInfo, request.getKeyword())
                .orderByDesc(Event::getEventTime);
        long page = Math.max(request.getPage(), 1);
        long size = Math.min(Math.max(request.getSize(), 1), properties.getEvent().getMaxPageSize());
        Page<Event> result = eventMapper.selectPage(new Page<>(page, size), wrapper);
        List<EventVO> records = result.getRecords().stream()
                .map(event -> toVO(event, bound.deviceMap().get(event.getDeviceTableId())))
                .toList();
        return PageResult.of(result, records);
    }

    public RefreshUrlVO refreshUrl(String eventId, RefreshUrlRequest request) {
        LoginUser loginUser = SecurityContextUtil.currentUser();
        Event event = findByEventId(eventId);
        deviceService.ensureUserBoundDevice(loginUser.getUserTableId(), event.getDeviceTableId());
        if (!StringUtils.hasText(event.getFileKey())) {
            throw new BizException(404, "事件文件不存在");
        }
        int expireSeconds = request.getExpireSeconds() == null
                ? (int) properties.getCache().getAccessUrlTtlSeconds()
                : request.getExpireSeconds();
        PresignedAccessInfo accessInfo = storageService.createAccessUrl(event.getFileKey(), expireSeconds);
        RefreshUrlVO vo = new RefreshUrlVO();
        vo.setEventId(event.getEventId());
        vo.setFileUrl(accessInfo.getFileUrl());
        vo.setExpireSeconds(accessInfo.getExpireSeconds());
        vo.setExpireAt(accessInfo.getExpireAt());
        return vo;
    }

    public Event findByEventId(String eventId) {
        Event event = eventMapper.selectOne(new LambdaQueryWrapper<Event>().eq(Event::getEventId, eventId));
        if (event == null) {
            throw BizException.notFound("事件不存在");
        }
        return event;
    }

    public EventVO toVO(Event event, Device device) {
        EventVO vo = new EventVO();
        vo.setEventId(event.getEventId());
        vo.setDeviceId(device == null ? null : device.getDeviceId());
        vo.setEventType(event.getEventType());
        vo.setAlarmInfo(event.getAlarmInfo());
        vo.setFileStatus(event.getFileStatus());
        vo.setPushStatus(event.getPushStatus());
        vo.setReadStatus(event.getReadStatus());
        vo.setEventTime(event.getEventTime());
        return vo;
    }

    private BoundDevices boundDevices(LoginUser loginUser) {
        List<UserDeviceBind> binds = deviceService.listBinds(loginUser.getUserTableId());
        List<Long> deviceTableIds = binds.stream().map(UserDeviceBind::getDeviceTableId).distinct().toList();
        Map<Long, Device> deviceMap = deviceService.listDeviceMapByIds(deviceTableIds);
        return new BoundDevices(deviceTableIds, deviceMap);
    }

    private void expireStaleUploads() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(properties.getCache().getUploadUrlTtlSeconds());
        int updated = eventMapper.update(null, new LambdaUpdateWrapper<Event>()
                .set(Event::getFileStatus, FileStatus.FAILED.name())
                .set(Event::getPushStatus, PushStatus.PENDING.name())
                .eq(Event::getFileStatus, FileStatus.UPLOADING.name())
                .le(Event::getEventTime, cutoff));
        if (updated > 0) {
            log.info("Expired stale uploading events, count={}, cutoff={}", updated, cutoff);
        }
    }

    private record BoundDevices(List<Long> deviceTableIds, Map<Long, Device> deviceMap) {
    }
}
