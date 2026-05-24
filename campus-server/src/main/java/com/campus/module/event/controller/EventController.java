package com.campus.module.event.controller;

import com.campus.common.result.ApiResult;
import com.campus.common.result.PageResult;
import com.campus.module.event.dto.EventSearchRequest;
import com.campus.module.event.dto.RefreshUrlRequest;
import com.campus.module.event.service.EventService;
import com.campus.module.event.vo.EventVO;
import com.campus.module.event.vo.RefreshUrlVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    @GetMapping("/unpulled")
    public ApiResult<List<EventVO>> unpulled() {
        return ApiResult.success(eventService.unpulled());
    }

    @PutMapping("/{eventId}/read")
    public ApiResult<Void> markRead(@PathVariable String eventId) {
        eventService.markRead(eventId);
        return ApiResult.success(null);
    }

    @DeleteMapping("/{event_id}")
    public ApiResult<Void> delete(@PathVariable("event_id") String eventId) {
        eventService.delete(eventId);
        return ApiResult.success(null);
    }

    @GetMapping("/search")
    public ApiResult<PageResult<EventVO>> search(@RequestParam(name = "device_id", required = false) String deviceId,
                                                 @RequestParam(defaultValue = "1") Long page,
                                                 @RequestParam(defaultValue = "10") Long size,
                                                 @RequestParam(name = "event_type", required = false) String eventType,
                                                 @RequestParam(name = "file_status", required = false) String fileStatus,
                                                 @RequestParam(name = "push_status", required = false) String pushStatus,
                                                 @RequestParam(name = "read_status", required = false) String readStatus,
                                                 @RequestParam(name = "start_time", required = false)
                                                 @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                                 @RequestParam(name = "end_time", required = false)
                                                 @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                                                 @RequestParam(required = false) String keyword) {
        EventSearchRequest request = new EventSearchRequest();
        request.setDeviceId(deviceId);
        request.setPage(page);
        request.setSize(size);
        request.setEventType(eventType);
        request.setFileStatus(fileStatus);
        request.setPushStatus(pushStatus);
        request.setReadStatus(readStatus);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setKeyword(keyword);
        return ApiResult.success(eventService.search(request));
    }

    @PostMapping("/{eventId}/refresh-url")
    public ApiResult<RefreshUrlVO> refreshUrl(@PathVariable String eventId,
                                              @Valid @RequestBody RefreshUrlRequest request) {
        return ApiResult.success("刷新成功", eventService.refreshUrl(eventId, request));
    }
}
