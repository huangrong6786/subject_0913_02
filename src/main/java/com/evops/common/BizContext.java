package com.evops.common;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * 解析一次业务写入的审计上下文：请求号（幂等键）、操作者、业务时区。
 * 优先取请求体中的 requestNo，其次取请求头，最后服务端生成。
 */
@Component
public class BizContext {

    public static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    public String requestNo(String provided) {
        if (provided != null && !provided.trim().isEmpty()) {
            return provided.trim();
        }
        String header = header("X-Request-No");
        return header != null ? header : "REQ-" + UUID.randomUUID().toString().replace("-", "");
    }

    public String operator() {
        String header = header("X-Operator");
        return header != null ? header : "system";
    }

    public String timezone() {
        String header = header("X-Biz-Timezone");
        return header != null ? header : DEFAULT_TIMEZONE;
    }

    private String header(String name) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        String value = attrs.getRequest().getHeader(name);
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }
}
