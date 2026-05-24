package com.campus.common.exception;

import com.campus.common.result.ApiResult;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBizException(BizException ex) {
        return ApiResult.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ApiResult<Void> handleBindException(Exception ex) {
        String message;
        if (ex instanceof MethodArgumentNotValidException methodEx) {
            message = methodEx.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        } else {
            message = ((BindException) ex).getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        return ApiResult.fail(-1, message);
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ApiResult<Void> handleBadRequest(Exception ex) {
        return ApiResult.fail(-1, "参数错误");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ApiResult<Void> handleAuthentication(AuthenticationException ex) {
        return ApiResult.fail(401, "未登录或 token 失效");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ApiResult<Void> handleAccessDenied(AccessDeniedException ex) {
        return ApiResult.fail(403, "无权限");
    }

    @ExceptionHandler({DataAccessException.class, RedisConnectionFailureException.class})
    public ApiResult<Void> handleInfrastructure(Exception ex) {
        log.error("Infrastructure exception", ex);
        return ApiResult.fail(500, "基础设施异常");
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ApiResult.fail(500, "服务器内部错误");
    }
}
