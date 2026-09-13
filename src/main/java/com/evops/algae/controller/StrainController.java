package com.evops.algae.controller;

import com.evops.algae.dto.StrainCreateRequest;
import com.evops.algae.entity.AlgaeStrain;
import com.evops.algae.service.StrainService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/algae/strains")
public class StrainController {

    private final StrainService strainService;

    public StrainController(StrainService strainService) {
        this.strainService = strainService;
    }

    @PostMapping
    public ApiResponse<AlgaeStrain> create(@Valid @RequestBody StrainCreateRequest request) {
        return ApiResponse.ok(strainService.create(request));
    }

    @PostMapping("/{strainNo}/accept")
    public ApiResponse<AlgaeStrain> accept(@PathVariable String strainNo,
                                           @RequestParam(required = false) String remark) {
        return ApiResponse.ok(strainService.accept(strainNo, remark));
    }

    @DeleteMapping("/{strainNo}")
    public ApiResponse<Void> delete(@PathVariable String strainNo) {
        strainService.delete(strainNo);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{strainNo}")
    public ApiResponse<AlgaeStrain> get(@PathVariable String strainNo) {
        return ApiResponse.ok(strainService.get(strainNo));
    }

    @GetMapping
    public ApiResponse<List<AlgaeStrain>> list() {
        return ApiResponse.ok(strainService.list());
    }
}
