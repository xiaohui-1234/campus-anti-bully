package com.campus.module.auth.controller;

import com.campus.common.result.ApiResult;
import com.campus.module.auth.dto.OpenidLoginRequest;
import com.campus.module.auth.dto.RefreshTokenRequest;
import com.campus.module.auth.dto.WxLoginRequest;
import com.campus.module.auth.service.AuthService;
import com.campus.module.auth.vo.LoginVO;
import com.campus.module.auth.vo.RefreshTokenVO;
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

    @PostMapping("/refresh")
    public ApiResult<RefreshTokenVO> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResult.success("刷新成功", authService.refresh(request));
    }

    @PostMapping("/wx/logout")
    public ApiResult<Void> logout(HttpServletRequest request) {
        authService.logout(request.getHeader("Authorization"));
        return ApiResult.success("退出登录成功", null);
    }
}
