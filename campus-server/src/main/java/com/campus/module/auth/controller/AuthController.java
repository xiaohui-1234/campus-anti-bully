package com.campus.module.auth.controller;

import com.campus.common.result.ApiResult;
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
import com.campus.module.auth.service.AuthService;
import com.campus.module.auth.vo.LoginVO;
import com.campus.module.auth.vo.RefreshTokenVO;
import com.campus.module.auth.vo.SecurityEmailChangeTicketVO;
import com.campus.module.user.vo.UserInfoVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/wx/login")
    public ApiResult<LoginVO> wxLogin(@Valid @RequestBody WxLoginRequest request) {
        return ApiResult.success("登录成功", authService.wxLogin(request));
    }

    @PostMapping("/openid/admin-login")
    public ApiResult<LoginVO> openidAdminLogin(@Valid @RequestBody OpenidLoginRequest request) {
        return ApiResult.success("登录成功", authService.openidAdminLogin(request));
    }

    @PostMapping("/email-code/send")
    public ApiResult<Void> sendEmailCode(@Valid @RequestBody EmailCodeSendRequest request,
                                         HttpServletRequest httpRequest) {
        authService.sendEmailCode(request, clientIp(httpRequest));
        return ApiResult.success("验证码已发送", null);
    }

    @PostMapping("/password/register")
    public ApiResult<LoginVO> passwordRegister(@Valid @RequestBody PasswordRegisterRequest request) {
        return ApiResult.success("注册成功", authService.passwordRegister(request));
    }

    @PostMapping("/password/login")
    public ApiResult<LoginVO> passwordLogin(@Valid @RequestBody PasswordLoginRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResult.success("登录成功", authService.passwordLogin(request, clientIp(httpRequest)));
    }

    @PostMapping("/account/activate")
    public ApiResult<UserInfoVO> activateAccount(@Valid @RequestBody AccountActivateRequest request) {
        return ApiResult.success("账号安全信息已设置", authService.activateAccount(request));
    }

    @PostMapping("/security-email/change/verify-old")
    public ApiResult<SecurityEmailChangeTicketVO> verifyOldSecurityEmail(
            @Valid @RequestBody SecurityEmailChangeVerifyOldRequest request) {
        return ApiResult.success("旧邮箱验证通过", authService.verifyOldSecurityEmail(request));
    }

    @PostMapping("/security-email/change/confirm")
    public ApiResult<UserInfoVO> confirmSecurityEmailChange(@Valid @RequestBody SecurityEmailChangeConfirmRequest request) {
        return ApiResult.success("安全邮箱已修改", authService.confirmSecurityEmailChange(request));
    }

    @PostMapping("/password/change")
    public ApiResult<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(request);
        return ApiResult.success("密码已修改", null);
    }

    @PostMapping("/password/reset")
    public ApiResult<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return ApiResult.success("密码已重置", null);
    }

    @PostMapping("/refresh")
    public ApiResult<RefreshTokenVO> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResult.success("刷新成功", authService.refresh(request));
    }

    @PostMapping("/wx/logout")
    public ApiResult<Void> logout(HttpServletRequest request) {
        authService.logout(request.getHeader("Authorization"));
        return ApiResult.success("退出成功", null);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
