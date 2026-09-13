package com.evops.algae.controller;

import com.evops.algae.audit.AuditService;
import com.evops.algae.entity.AuditLog;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 审计链查询：凭请求号回溯操作者、业务时区与版本快照。 */
@RestController
@RequestMapping("/api/algae/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/requests/{requestNo}")
    public ApiResponse<List<AuditLog>> byRequest(@PathVariable String requestNo) {
        return ApiResponse.ok(auditService.listByRequestNo(requestNo));
    }
}
