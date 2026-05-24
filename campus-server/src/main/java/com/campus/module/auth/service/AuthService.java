package com.campus.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.constants.RedisKeys;
import com.campus.common.enums.UserRole;
import com.campus.common.exception.BizException;
import com.campus.common.util.HashUtil;
import com.campus.common.util.IdGenerator;
import com.campus.config.CampusProperties;
import com.campus.infrastructure.wx.WxMiniAppClient;
import com.campus.module.auth.dto.OpenidLoginRequest;
import com.campus.module.auth.dto.RefreshTokenRequest;
import com.campus.module.auth.dto.WxLoginRequest;
import com.campus.module.auth.vo.LoginVO;
import com.campus.module.auth.vo.RefreshTokenVO;
import com.campus.module.user.entity.User;
import com.campus.module.user.mapper.UserMapper;
import com.campus.module.user.vo.UserInfoVO;
import com.campus.security.JwtTokenProvider;
import com.campus.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final WxMiniAppClient wxMiniAppClient;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final CampusProperties properties;

    @Transactional(rollbackFor = Exception.class)
    public LoginVO wxLogin(WxLoginRequest request) {
        String openid = wxMiniAppClient.exchangeCodeForOpenid(request.getCode());
        String openidHash = HashUtil.sha256(openid);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenidHash, openidHash));
        boolean newUser = false;
        if (user == null) {
            user = new User();
            user.setUserId(IdGenerator.userId());
            user.setOpenidHash(openidHash);
            user.setNickname("微信用户");
            user.setRole(UserRole.USER.name());
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user);
            newUser = true;
        }
        log.info("User login success, user_id={}, is_new_user={}", user.getUserId(), newUser);
        return issueTokens(user, newUser);
    }

    public LoginVO openidAdminLogin(OpenidLoginRequest request) {
        String openidHash = HashUtil.sha256(request.getOpenid());
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenidHash, openidHash));
        if (user == null || !UserRole.ADMIN.name().equals(user.getRole())) {
            throw BizException.unauthorized("管理员不存在或无权限");
        }
        log.info("Admin login success, user_id={}", user.getUserId());
        return issueTokens(user, false);
    }

    public RefreshTokenVO refresh(RefreshTokenRequest request) {
        LoginUser loginUser = jwtTokenProvider.parseRefreshToken(request.getRefreshToken());
        User user = userMapper.selectById(loginUser.getUserTableId());
        if (user == null) {
            log.warn("Token refresh failed, user_id={} not found", loginUser.getUserId());
            throw BizException.unauthorized("刷新失败，请重新登录");
        }
        String accessToken = jwtTokenProvider.createAccessToken(new LoginUser(user.getId(), user.getUserId(), user.getRole()));
        return new RefreshTokenVO(accessToken, "Bearer", jwtTokenProvider.accessTokenExpiresIn());
    }

    public void logout(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return;
        }
        String token = authorization.substring(7);
        String tokenId = jwtTokenProvider.tokenId(token);
        stringRedisTemplate.opsForValue().set(
                RedisKeys.jwtBlacklist(tokenId),
                "DONE",
                properties.getCache().getJwtBlacklistTtlSeconds(),
                TimeUnit.SECONDS
        );
    }

    private LoginVO issueTokens(User user, boolean newUser) {
        LoginUser loginUser = new LoginUser(user.getId(), user.getUserId(), user.getRole());
        LoginVO vo = new LoginVO();
        vo.setAccessToken(jwtTokenProvider.createAccessToken(loginUser));
        vo.setRefreshToken(jwtTokenProvider.createRefreshToken(loginUser));
        vo.setExpiresIn(jwtTokenProvider.accessTokenExpiresIn());
        vo.setUserInfo(toUserInfo(user, newUser));
        return vo;
    }

    private UserInfoVO toUserInfo(User user, boolean newUser) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getUserId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setRole(user.getRole());
        vo.setCreatedAt(user.getCreateTime());
        vo.setIsNewUser(newUser);
        return vo;
    }
}
