package com.campus.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {

    private int code;
    private String message;
    private T data;

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(0, "success", data);
    }

    public static <T> ApiResult<T> success(String message, T data) {
        return new ApiResult<>(0, message, data);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null);
    }
}
