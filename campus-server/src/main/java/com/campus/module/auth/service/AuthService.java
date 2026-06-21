package com.campus.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.constants.RedisKeys;
import com.campus.common.enums.UserRole;
import com.campus.common.exception.BizException;
import com.campus.common.util.HashUtil;
import com.campus.common.util.IdGenerator;
import com.campus.config.CampusProperties;
import com.campus.infrastructure.wx.WxMiniAppClient;
import com.campus.module.auth.dto.AccountActivateRequest;
import com.campus.module.auth.dto.EmailCodeSendRequest;
import com.campus.module.auth.dto.OpenidLoginRequest;
import com.campus.module.auth.dto.PasswordChangeRequest;
import com.campus.module.auth.dto.PasswordLoginRequest;
import com.campus.module.auth.dto.PasswordRegisterRequest;
import com.campus.module.auth.dto.PasswordResetRequest;
import com.campus.module.auth.dto.RefreshTokenRequest;
import com.campus.module.auth.dto.SecurityEmailChangeConfirmRequest;
import com.campus.module.auth.dto.SecurityEmailChangeVerifyOldRequest;
import com.campus.module.auth.dto.WxLoginRequest;
import com.campus.module.auth.enums.EmailCodeScene;
import com.campus.module.auth.vo.LoginVO;
import com.campus.module.auth.vo.RefreshTokenVO;
import com.campus.module.auth.vo.SecurityEmailChangeTicketVO;
import com.campus.module.user.entity.User;
import com.campus.module.user.mapper.UserMapper;
import com.campus.module.user.vo.UserInfoVO;
import com.campus.security.JwtTokenProvider;
import com.campus.security.LoginUser;
import com.campus.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String LOGIN_ERROR = "账号或密码错误";

    private final WxMiniAppClient wxMiniAppClient;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final CampusProperties properties;
    private final AccountCryptoService accountCryptoService;
    private final EmailCodeService emailCodeService;
    private final PasswordPolicyService passwordPolicyService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

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
            user.setSecurityEmailVerified(false);
            user.setTokenVersion(0);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
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
            throw BizException.unauthorized("管理员账号不存在或无权限");
        }
        log.info("Admin login success, user_id={}", user.getUserId());
        return issueTokens(user, false);
    }

    public void sendEmailCode(EmailCodeSendRequest request, String ip) {
        EmailCodeScene scene = EmailCodeScene.from(request.getScene());
        User currentUser = scene.isLoginRequired() ? currentUser() : null;
        String targetEmail = request.getSecurityEmail();

        if (scene == EmailCodeScene.CHANGE_SECURITY_EMAIL_OLD) {
            ensureBoundSecurityEmail(currentUser);
            targetEmail = accountCryptoService.decryptEmail(currentUser.getSecurityEmailCipher());
        } else if (scene == EmailCodeScene.CHANGE_SECURITY_EMAIL_NEW) {
            ensureSecurityEmailChangeGrant(currentUser, request.getChangeTicket());
            if (!StringUtils.hasText(targetEmail)) {
                throw new BizException(-1, "请输入安全邮箱");
            }
        } else if (scene.isEmailRequired() && !StringUtils.hasText(targetEmail)) {
            throw new BizException(-1, "请输入安全邮箱");
        }

        if (scene == EmailCodeScene.PASSWORD_REGISTER
                || scene == EmailCodeScene.ACTIVATE_ACCOUNT
                || scene == EmailCodeScene.CHANGE_SECURITY_EMAIL_NEW) {
            ensureSecurityEmailUnused(targetEmail, currentUser == null ? null : currentUser.getId());
        }

        if (scene == EmailCodeScene.RESET_PASSWORD) {
            User user = findBySecurityEmail(targetEmail);
            if (user == null || !isSecurityEmailVerified(user) || !StringUtils.hasText(user.getPasswordHash())) {
                return;
            }
        }

        emailCodeService.send(scene, targetEmail, ip);
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginVO passwordRegister(PasswordRegisterRequest request) {
        passwordPolicyService.validate(request.getPassword());
        ensureSecurityEmailUnused(request.getSecurityEmail(), null);
        emailCodeService.verifyAndConsume(EmailCodeScene.PASSWORD_REGISTER, request.getSecurityEmail(), request.getVerifyCode());

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUserId(IdGenerator.userId());
        user.setNickname(defaultNickname(request.getSecurityEmail()));
        user.setRole(UserRole.USER.name());
        applySecurityEmail(user, request.getSecurityEmail(), now);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPasswordSetTime(now);
        user.setTokenVersion(0);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insert(user);
        return issueTokens(user, true);
    }

    public LoginVO passwordLogin(PasswordLoginRequest request, String ip) {
        String loginId = request.getLoginId().trim();
        boolean emailLogin = isEmailLogin(loginId);
        String loginSubject = emailLogin ? accountCryptoService.normalizeEmail(loginId) : loginId;
        loginAttemptService.assertAllowed(loginSubject, ip);
        User user = emailLogin ? findBySecurityEmail(loginSubject) : findByUserId(loginSubject);
        if (!canPasswordLogin(user)
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(loginSubject, ip);
            throw BizException.unauthorized(LOGIN_ERROR);
        }
        loginAttemptService.clearSubject(loginSubject);
        log.info("Password login success, user_id={}", user.getUserId());
        return issueTokens(user, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO activateAccount(AccountActivateRequest request) {
        User user = currentUser();
        if (!StringUtils.hasText(user.getOpenidHash())) {
            throw new BizException(-1, "当前入口仅支持小程序账号设置安全邮箱和密码");
        }
        if (StringUtils.hasText(user.getSecurityEmailHash()) || StringUtils.hasText(user.getPasswordHash())) {
            throw BizException.conflict("账号已设置安全邮箱或密码");
        }
        passwordPolicyService.validate(request.getPassword());
        ensureSecurityEmailUnused(request.getSecurityEmail(), user.getId());
        emailCodeService.verifyAndConsume(EmailCodeScene.ACTIVATE_ACCOUNT, request.getSecurityEmail(), request.getVerifyCode());
        LocalDateTime now = LocalDateTime.now();
        applySecurityEmail(user, request.getSecurityEmail(), now);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPasswordSetTime(now);
        user.setUpdateTime(now);
        userMapper.updateById(user);
        return toUserInfo(user, false);
    }

    public SecurityEmailChangeTicketVO verifyOldSecurityEmail(SecurityEmailChangeVerifyOldRequest request) {
        User user = currentUser();
        ensureBoundSecurityEmail(user);
        String oldEmail = accountCryptoService.decryptEmail(user.getSecurityEmailCipher());
        emailCodeService.verifyAndConsume(EmailCodeScene.CHANGE_SECURITY_EMAIL_OLD, oldEmail, request.getOldVerifyCode());
        String ticket = issueSecurityEmailChangeTicket(user);
        return new SecurityEmailChangeTicketVO(
                ticket,
                properties.getSecurity().getSecurityEmailChange().getGrantTtlSeconds()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO confirmSecurityEmailChange(SecurityEmailChangeConfirmRequest request) {
        User user = currentUser();
        ensureBoundSecurityEmail(user);
        ensureSecurityEmailChangeGrant(user, request.getChangeTicket());
        String oldEmail = accountCryptoService.decryptEmail(user.getSecurityEmailCipher());
        String newEmail = accountCryptoService.normalizeEmail(request.getNewSecurityEmail());
        if (accountCryptoService.emailHash(oldEmail).equals(accountCryptoService.emailHash(newEmail))) {
            throw new BizException(-1, "新安全邮箱不能与当前邮箱相同");
        }
        ensureSecurityEmailUnused(newEmail, user.getId());
        String ticketHash = securityEmailChangeTicketHash(request.getChangeTicket());
        emailCodeService.verifyAndConsumeWithGrant(
                EmailCodeScene.CHANGE_SECURITY_EMAIL_NEW,
                newEmail,
                request.getNewVerifyCode(),
                securityEmailChangeGrantKeyByHash(user, ticketHash),
                RedisKeys.securityEmailChangeCurrent(user.getUserId()),
                user.getSecurityEmailHash(),
                ticketHash
        );
        LocalDateTime now = LocalDateTime.now();
        applySecurityEmail(user, newEmail, now);
        user.setUpdateTime(now);
        userMapper.updateById(user);
        return toUserInfo(user, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(PasswordChangeRequest request) {
        User user = currentUser();
        if (!StringUtils.hasText(user.getPasswordHash())
                || !passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw BizException.unauthorized(LOGIN_ERROR);
        }
        passwordPolicyService.validate(request.getNewPassword());
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordSetTime(LocalDateTime.now());
        user.setTokenVersion(tokenVersion(user) + 1);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(PasswordResetRequest request) {
        passwordPolicyService.validate(request.getNewPassword());
        User user = findBySecurityEmail(request.getSecurityEmail());
        if (user == null || !isSecurityEmailVerified(user)) {
            throw BizException.unauthorized(LOGIN_ERROR);
        }
        emailCodeService.verifyAndConsume(EmailCodeScene.RESET_PASSWORD, request.getSecurityEmail(), request.getVerifyCode());
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordSetTime(LocalDateTime.now());
        user.setTokenVersion(tokenVersion(user) + 1);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public RefreshTokenVO refresh(RefreshTokenRequest request) {
        LoginUser loginUser = jwtTokenProvider.parseRefreshToken(request.getRefreshToken());
        User user = userMapper.selectById(loginUser.getUserTableId());
        if (user == null || tokenVersion(user) != loginUser.getTokenVersion()) {
            log.warn("Token refresh failed, user_id={} invalid", loginUser.getUserId());
            throw BizException.unauthorized("登录状态已失效，请重新登录");
        }
        String accessToken = jwtTokenProvider.createAccessToken(toLoginUser(user));
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
        LoginUser loginUser = toLoginUser(user);
        LoginVO vo = new LoginVO();
        vo.setAccessToken(jwtTokenProvider.createAccessToken(loginUser));
        vo.setRefreshToken(jwtTokenProvider.createRefreshToken(loginUser));
        vo.setExpiresIn(jwtTokenProvider.accessTokenExpiresIn());
        vo.setUserInfo(toUserInfo(user, newUser));
        return vo;
    }

    private LoginUser toLoginUser(User user) {
        return new LoginUser(user.getId(), user.getUserId(), user.getRole(), tokenVersion(user));
    }

    private UserInfoVO toUserInfo(User user, boolean newUser) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getUserId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setSecurityEmailMasked(accountCryptoService.maskEncryptedEmail(user.getSecurityEmailCipher()));
        vo.setSecurityEmailVerified(isSecurityEmailVerified(user));
        vo.setPasswordEnabled(StringUtils.hasText(user.getPasswordHash()));
        vo.setRole(user.getRole());
        vo.setCreatedAt(user.getCreateTime());
        vo.setIsNewUser(newUser);
        return vo;
    }

    private void applySecurityEmail(User user, String email, LocalDateTime now) {
        String normalized = accountCryptoService.normalizeEmail(email);
        user.setSecurityEmailHash(accountCryptoService.emailHash(normalized));
        user.setSecurityEmailCipher(accountCryptoService.encryptEmail(normalized));
        user.setSecurityEmailVerified(true);
        user.setSecurityEmailVerifiedTime(now);
        user.setSecurityEmailUpdateTime(now);
    }

    private void ensureSecurityEmailUnused(String email, Long currentUserTableId) {
        String emailHash = accountCryptoService.emailHash(email);
        User existed = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getSecurityEmailHash, emailHash));
        if (existed != null && (currentUserTableId == null || !existed.getId().equals(currentUserTableId))) {
            throw BizException.conflict("该安全邮箱已被其他账号使用");
        }
    }

    private void ensureBoundSecurityEmail(User user) {
        if (user == null || !StringUtils.hasText(user.getSecurityEmailCipher()) || !isSecurityEmailVerified(user)) {
            throw new BizException(-1, "当前账号未绑定安全邮箱");
        }
    }

    private String issueSecurityEmailChangeTicket(User user) {
        byte[] bytes = new byte[Math.max(16, properties.getSecurity().getSecurityEmailChange().getTicketBytes())];
        secureRandom.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String ticketHash = securityEmailChangeTicketHash(ticket);
        String currentKey = RedisKeys.securityEmailChangeCurrent(user.getUserId());
        String oldTicketHash = stringRedisTemplate.opsForValue().get(currentKey);
        if (StringUtils.hasText(oldTicketHash)) {
            stringRedisTemplate.delete(securityEmailChangeGrantKeyByHash(user, oldTicketHash));
        }
        stringRedisTemplate.opsForValue().set(
                securityEmailChangeGrantKeyByHash(user, ticketHash),
                user.getSecurityEmailHash(),
                properties.getSecurity().getSecurityEmailChange().getGrantTtlSeconds(),
                TimeUnit.SECONDS
        );
        stringRedisTemplate.opsForValue().set(
                currentKey,
                ticketHash,
                properties.getSecurity().getSecurityEmailChange().getGrantTtlSeconds(),
                TimeUnit.SECONDS
        );
        return ticket;
    }

    private void ensureSecurityEmailChangeGrant(User user, String ticket) {
        if (user == null || !StringUtils.hasText(ticket)) {
            throw new BizException(-1, "请先验证旧邮箱");
        }
        String ticketHash = securityEmailChangeTicketHash(ticket);
        String currentTicketHash = stringRedisTemplate.opsForValue().get(RedisKeys.securityEmailChangeCurrent(user.getUserId()));
        if (!ticketHash.equals(currentTicketHash)) {
            throw BizException.unauthorized("邮箱修改凭证无效，请重新验证旧邮箱");
        }
        String savedSecurityEmailHash = stringRedisTemplate.opsForValue().get(securityEmailChangeGrantKeyByHash(user, ticketHash));
        if (!StringUtils.hasText(savedSecurityEmailHash) || !savedSecurityEmailHash.equals(user.getSecurityEmailHash())) {
            throw BizException.unauthorized("邮箱修改凭证无效，请重新验证旧邮箱");
        }
    }

    private String securityEmailChangeGrantKeyByHash(User user, String ticketHash) {
        return RedisKeys.securityEmailChangeGrant(user.getUserId(), ticketHash);
    }

    private String securityEmailChangeTicketHash(String ticket) {
        return accountCryptoService.hmacHex(ticket);
    }

    private boolean canPasswordLogin(User user) {
        if (user == null || !StringUtils.hasText(user.getPasswordHash())) {
            return false;
        }
        return isSecurityEmailVerified(user);
    }

    private boolean isSecurityEmailVerified(User user) {
        return user != null && Boolean.TRUE.equals(user.getSecurityEmailVerified());
    }

    private User findBySecurityEmail(String email) {
        String emailHash = accountCryptoService.emailHash(email);
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getSecurityEmailHash, emailHash));
    }

    private User findByUserId(String userId) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserId, userId));
    }

    private User currentUser() {
        Long userTableId = SecurityContextUtil.currentUser().getUserTableId();
        User user = userMapper.selectById(userTableId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private boolean isEmailLogin(String loginId) {
        return loginId.contains("@");
    }

    private int tokenVersion(User user) {
        return user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }

    private String defaultNickname(String email) {
        String normalized = accountCryptoService.normalizeEmail(email);
        int at = normalized.indexOf('@');
        return at > 0 ? normalized.substring(0, at) : "user";
    }
}
