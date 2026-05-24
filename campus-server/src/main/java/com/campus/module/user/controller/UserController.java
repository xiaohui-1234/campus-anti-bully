package com.campus.module.user.controller;

import com.campus.common.result.ApiResult;
import com.campus.module.user.dto.UpdateUserRequest;
import com.campus.module.user.service.UserService;
import com.campus.module.user.vo.AvatarVO;
import com.campus.module.user.vo.UserInfoVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResult<UserInfoVO> me() {
        return ApiResult.success(userService.me());
    }

    @PutMapping("/me")
    public ApiResult<UserInfoVO> updateMe(@Valid @RequestBody UpdateUserRequest request) {
        return ApiResult.success("修改成功", userService.updateMe(request));
    }

    @PostMapping("/me/avatar")
    public ApiResult<AvatarVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResult.success("头像上传成功", userService.uploadAvatar(file));
    }
}
