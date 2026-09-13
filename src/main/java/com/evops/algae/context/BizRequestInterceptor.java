package com.evops.algae.context;

import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * 从请求头解析请求号/操作者/业务时区，写入 {@link BizRequestContext}。
 * 请求号缺省时生成 UUID，保证审计链不中断。
 */
public class BizRequestInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestNo = request.getHeader(BizRequestContext.HEADER_REQUEST_NO);
        if (!StringUtils.hasText(requestNo)) {
            requestNo = "REQ-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        }
        String operator = request.getHeader(BizRequestContext.HEADER_OPERATOR);
        String bizTz = request.getHeader(BizRequestContext.HEADER_BIZ_TZ);
        BizRequestContext.set(new BizRequestContext.RequestInfo(requestNo.trim(), operator, bizTz));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BizRequestContext.clear();
    }
}
