package com.example.spring_vue_demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.spring_vue_demo.entity.Result;
import com.example.spring_vue_demo.entity.Staff;
import com.example.spring_vue_demo.mapper.StaffMapper;
import com.example.spring_vue_demo.param.LoginParam;
import com.example.spring_vue_demo.service.AuthTokenService;
import com.example.spring_vue_demo.service.LoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginServiceImpl extends ServiceImpl<StaffMapper, Staff> implements LoginService {
    private final StaffMapper staffMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;

    @Override
    @Transactional
    public Result login(LoginParam loginParam) {
        Staff staff = staffMapper.selectOne(new LambdaQueryWrapper<Staff>().eq(Staff::getPhone, loginParam.getPhone()));
        if (staff == null || staff.getStatus() == null || staff.getStatus() != 0 || !matches(loginParam.getPassword(), staff.getPassword())) {
            return Result.error("账号或者密码错误");
        }
        if (!isHash(staff.getPassword())) {
            staff.setPassword(passwordEncoder.encode(loginParam.getPassword()));
            if (staff.getAuthVersion() == null) staff.setAuthVersion(0);
            staffMapper.updateById(staff);
        }
        return Result.success(authTokenService.issue(staff));
    }

    private boolean matches(String raw, String encoded) {
        return raw != null && encoded != null && (isHash(encoded) ? passwordEncoder.matches(raw, encoded) : constantTimeEquals(raw, encoded));
    }
    private boolean isHash(String value) { return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"); }
    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(left.getBytes(java.nio.charset.StandardCharsets.UTF_8), right.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
