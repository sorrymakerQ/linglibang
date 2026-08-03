package com.linlibang.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 是否成功 */
    private Boolean success;

    /** 状态码 */
    private Integer code;

    /** 提示消息 */
    private String message;

    /** 响应数据 */
    private T data;

    // ==================== 成功响应 ====================

    public static <T> Result<T> ok(T data) {
        return new Result<>(true, 200, "操作成功", data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(true, 200, message, data);
    }

    // ==================== 失败响应 ====================

    public static <T> Result<T> fail(String message) {
        return new Result<>(false, 500, message, null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(false, code, message, null);
    }
}
