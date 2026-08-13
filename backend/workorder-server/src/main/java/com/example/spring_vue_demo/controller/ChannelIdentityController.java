package com.example.spring_vue_demo.controller;

import com.example.spring_vue_demo.entity.Result;
import com.example.spring_vue_demo.service.ChannelIdentityService;
import com.example.workorder.api.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/channel/identity")
@RequiredArgsConstructor
public class ChannelIdentityController {
    private final ChannelIdentityService service;

    @PostMapping("/binding-challenges")
    public Result challenge(@RequestHeader("Authorization") String authorization) {
        return Result.success(service.createChallenge(authorization));
    }
    @PostMapping("/internal/confirm")
    public Result confirm(@RequestHeader("X-Workorder-Service-Key") String serviceKey, @Valid @RequestBody ConfirmBindingRequest request) {
        return call(() -> service.confirm(serviceKey, request));
    }
    @PostMapping("/internal/find")
    public Result find(@RequestHeader("X-Workorder-Service-Key") String serviceKey, @Valid @RequestBody FeishuIdentityRequest request) {
        return call(() -> service.find(serviceKey, request));
    }
    @PostMapping("/unbind")
    public Result unbind(@RequestHeader("Authorization") String authorization) {
        return call(() -> { service.unbind(authorization); return null; });
    }
    @PostMapping("/internal/exchange")
    public Result exchange(@RequestHeader("X-Workorder-Service-Key") String serviceKey, @Valid @RequestBody ChannelTokenRequest request) {
        return call(() -> service.exchange(serviceKey, request));
    }
    private Result call(java.util.concurrent.Callable<Object> action) {
        try { Object data = action.call(); return data == null ? Result.success() : Result.success(data); }
        catch (SecurityException ex) { return Result.error(403, ex.getMessage()); }
        catch (Exception ex) { return Result.error(ex.getMessage()); }
    }
}
