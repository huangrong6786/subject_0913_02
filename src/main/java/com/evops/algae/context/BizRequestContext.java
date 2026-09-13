package com.evops.algae.context;

/**
 * 业务请求上下文（线程级）：保存请求号、操作者编码与业务时区。
 * 由拦截器在请求进入时写入、结束时清理；业务服务/审计组件直接读取。
 */
public final class BizRequestContext {

    /** 请求头：请求号（幂等与审计追踪）。 */
    public static final String HEADER_REQUEST_NO = "X-Request-No";
    /** 请求头：操作者编码。 */
    public static final String HEADER_OPERATOR = "X-Operator";
    /** 请求头：业务时区，如 Asia/Shanghai。 */
    public static final String HEADER_BIZ_TZ = "X-Biz-Tz";

    public static final String DEFAULT_TIMEZONE = "Asia/Shanghai";
    public static final String DEFAULT_OPERATOR = "anonymous";

    private static final ThreadLocal<RequestInfo> HOLDER = new ThreadLocal<>();

    private BizRequestContext() {
    }

    public static void set(RequestInfo info) {
        HOLDER.set(info);
    }

    public static RequestInfo get() {
        RequestInfo info = HOLDER.get();
        if (info == null) {
            // 非 HTTP 入口（测试/定时任务）兜底，避免审计缺字段。
            info = new RequestInfo(null, DEFAULT_OPERATOR, DEFAULT_TIMEZONE);
            HOLDER.set(info);
        }
        return info;
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 一次业务请求的可追溯要素。 */
    public static class RequestInfo {
        private final String requestNo;
        private final String operatorCode;
        private final String bizTimezone;

        public RequestInfo(String requestNo, String operatorCode, String bizTimezone) {
            this.requestNo = requestNo;
            this.operatorCode = operatorCode == null || operatorCode.trim().isEmpty()
                    ? DEFAULT_OPERATOR : operatorCode.trim();
            this.bizTimezone = bizTimezone == null || bizTimezone.trim().isEmpty()
                    ? DEFAULT_TIMEZONE : bizTimezone.trim();
        }

        public String getRequestNo() { return requestNo; }
        public String getOperatorCode() { return operatorCode; }
        public String getBizTimezone() { return bizTimezone; }
    }
}
