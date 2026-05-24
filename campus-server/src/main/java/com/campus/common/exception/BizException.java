package com.campus.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(1, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException unauthorized(String message) {
        return new BizException(401, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }

    public static BizException notFound(String message) {
        return new BizException(404, message);
    }

    public static BizException conflict(String message) {
        return new BizException(409, message);
    }
}
