package com.evops.common;

/**
 * 业务异常：由全局异常处理器转换为统一失败返回。
 */
public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}
