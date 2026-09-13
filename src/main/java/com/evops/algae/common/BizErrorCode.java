package com.evops.algae.common;

/** 业务错误码：用于统一 REST 返回的 code 字段，便于调用方分支处理。 */
public enum BizErrorCode {
    NOT_FOUND("NOT_FOUND", "记录不存在"),
    BIZ_KEY_DUPLICATED("BIZ_KEY_DUPLICATED", "关键业务键已存在"),
    ILLEGAL_STATUS("ILLEGAL_STATUS", "当前状态不允许该操作"),
    RECORD_LOCKED("RECORD_LOCKED", "已验收或已落账记录不能删除"),
    INVALID_MEASUREMENT("INVALID_MEASUREMENT", "测量值不合法"),
    VOLUME_EXCEEDED("VOLUME_EXCEEDED", "体积超出反应器容量或批次当前体积"),
    CONCURRENT_UPDATE("CONCURRENT_UPDATE", "并发更新冲突，请重试"),
    BAD_REQUEST("BAD_REQUEST", "请求参数不合法");

    private final String code;
    private final String defaultMessage;

    BizErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() { return code; }
    public String getDefaultMessage() { return defaultMessage; }
}
