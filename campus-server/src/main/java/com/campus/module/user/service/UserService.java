package com.campus.module.user.service;

import com.campus.common.exception.BizException;
import com.campus.common.util.FileUtil;
import com.campus.config.CampusProperties;
import com.campus.module.storage.service.StorageService;
import com.campus.module.user.dto.UpdateUserRequest;
import com.campus.module.user.entity.User;
import com.campus.module.user.mapper.UserMapper;
import com.campus.module.user.vo.AvatarVO;
import com.campus.module.user.vo.UserInfoVO;
import com.campus.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final StorageService storageService;
    private final CampusProperties properties;

    public UserInfoVO me() {
        return toVO(currentUser());
    }

    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO updateMe(UpdateUserRequest request) {
        User user = currentUser();
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        userMapper.updateById(user);
        return toVO(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public AvatarVO uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(-1, "头像文件不能为空");
        }
        long maxSize = properties.getStorage().getAvatarMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new BizException(-1, "头像文件大小超过限制");
        }
        String ext = FileUtil.extension(file.getOriginalFilename(), "jpg");
        if (!FileUtil.isAllowedAvatar(ext)) {
            throw new BizException(-1, "头像格式仅支持 jpg/jpeg/png/webp");
        }
        User user = currentUser();
        String avatarUrl = storageService.uploadAvatar(user.getUserId(), file, ext);
        user.setAvatarUrl(avatarUrl);
        userMapper.updateById(user);
        return new AvatarVO(avatarUrl);
    }

    private User currentUser() {
        Long userTableId = SecurityContextUtil.currentUser().getUserTableId();
        User user = userMapper.selectById(userTableId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private UserInfoVO toVO(User user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getUserId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setRole(user.getRole());
        vo.setCreatedAt(user.getCreateTime());
        vo.setIsNewUser(false);
        return vo;
    }
}
