package com.campus.module.admin.service;

import com.campus.module.admin.vo.SystemInfoVO;
import com.campus.module.device.mapper.DeviceMapper;
import com.campus.module.event.mapper.EventMapper;
import com.campus.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminSystemService {

    private final UserMapper userMapper;
    private final DeviceMapper deviceMapper;
    private final EventMapper eventMapper;

    public SystemInfoVO info() {
        return SystemInfoVO.builder()
                .applicationName("campus-server")
                .javaVersion(System.getProperty("java.version"))
                .osName(System.getProperty("os.name"))
                .userCount(userMapper.selectCount(null))
                .deviceCount(deviceMapper.selectCount(null))
                .eventCount(eventMapper.selectCount(null))
                .build();
    }
}
