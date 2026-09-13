package com.evops.common;

import com.evops.algae.common.BizErrorCode;
import com.evops.algae.context.BizRequestContext;

/**
 * 统一 REST 返回：success / code / message / data / requestNo。
 * requestNo 回显便于调用方凭请求号对账与排查。
 */
public class ApiResponse<T> {
    private boolean success;
    private String code;
    private String message;
    private T data;
    private String requestNo;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.code = "OK";
        response.message = "OK";
        response.data = data;
        response.requestNo = currentRequestNo();
        return response;
    }

    public static <T> ApiResponse<T> fail(String message) {
        return fail("BIZ_ERROR", message);
    }

    public static <T> ApiResponse<T> fail(BizErrorCode errorCode, String message) {
        return fail(errorCode.getCode(), message);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.code = code;
        response.message = message;
        response.requestNo = currentRequestNo();
        return response;
    }

    private static String currentRequestNo() {
        try {
            return BizRequestContext.get().getRequestNo();
        } catch (Exception ignore) {
            return null;
        }
    }

    public boolean isSuccess() { return success; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public String getRequestNo() { return requestNo; }
}
