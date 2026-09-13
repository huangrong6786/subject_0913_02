package com.evops.algae.controller;

import com.evops.algae.dto.ReactorCreateRequest;
import com.evops.algae.entity.AlgaeReactor;
import com.evops.algae.service.ReactorService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/algae/reactors")
public class ReactorController {

    private final ReactorService reactorService;

    public ReactorController(ReactorService reactorService) {
        this.reactorService = reactorService;
    }

    @PostMapping
    public ApiResponse<AlgaeReactor> create(@Valid @RequestBody ReactorCreateRequest request) {
        return ApiResponse.ok(reactorService.create(request));
    }

    @DeleteMapping("/{reactorCode}")
    public ApiResponse<Void> delete(@PathVariable String reactorCode) {
        reactorService.delete(reactorCode);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{reactorCode}")
    public ApiResponse<AlgaeReactor> get(@PathVariable String reactorCode) {
        return ApiResponse.ok(reactorService.get(reactorCode));
    }

    @GetMapping
    public ApiResponse<List<AlgaeReactor>> list() {
        return ApiResponse.ok(reactorService.list());
    }
}
