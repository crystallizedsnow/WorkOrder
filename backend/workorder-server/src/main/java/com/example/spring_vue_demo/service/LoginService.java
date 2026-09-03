package com.example.spring_vue_demo.service;

import com.example.spring_vue_demo.entity.Result;
import com.example.spring_vue_demo.param.LoginParam;
import com.example.workorder.api.dto.AuthTokenResponse;

public interface LoginService {
    Result login(LoginParam loginParam);
    AuthTokenResponse loginToken(LoginParam loginParam);
}
