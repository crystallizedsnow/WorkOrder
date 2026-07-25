package com.example.spring_vue_demo.controller;

import com.example.spring_vue_demo.entity.Result;
import com.example.spring_vue_demo.entity.Staff;
import com.example.spring_vue_demo.param.LoginParam;
import com.example.spring_vue_demo.service.LoginService;
import com.example.spring_vue_demo.utils.LogUtils;
import com.example.spring_vue_demo.utils.StaffHolder;
import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 *
 * @author WangDayu
 * @date 2025/6/1
 */

@RestController
@Tag(name="登录控制")
@RequestMapping("/user")
public class LoginController {
    @Autowired
    private LoginService loginService;
    @ApiOperationSupport(order = 1)
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result login(@RequestBody LoginParam loginParam) {
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/user/login", "POST", loginParam);
        try {
            Result result = loginService.login(loginParam);
            LogUtils.returnLog(traceId, "/user/login", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/user/login", loginParam, e);
            throw e;
        }
    }
}
