package com.evops.algae.common;

/** 业务异常：由全局异常处理器转成统一 REST 返回，不打印系统错误堆栈。 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final BizErrorCode errorCode;

    public BizException(BizErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BizException(BizErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BizErrorCode getErrorCode() {
        return errorCode;
    }
}
