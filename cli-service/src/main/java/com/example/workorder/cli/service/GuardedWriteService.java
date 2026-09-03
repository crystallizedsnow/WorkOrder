package com.example.workorder.cli.service;

import com.alibaba.fastjson.JSON;
import com.example.workorder.cli.guard.AuthorizedWriteRequest;
import com.example.workorder.cli.guard.WriteExecutionGuard;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class GuardedWriteService {
    private final WriteExecutionGuard guard;
    private final WriteService writes;

    public GuardedWriteService(WriteExecutionGuard guard, WriteService writes) {
        this.guard = guard;
        this.writes = writes;
    }

    public Object execute(String previewId, String dataCode, Map<String, Object> params, String token, String traceId) {
        AuthorizedWriteRequest authorized = guard.authorizeAndClaim(previewId, dataCode, params, token);
        Object result;
        try {
            result = writes.execute(authorized.dataCode(), authorized.params(), token, traceId);
        } catch (RuntimeException error) {
            guard.complete(authorized.preview(), false, error.getMessage());
            throw error;
        }
        boolean success = isSuccess(result);
        guard.complete(authorized.preview(), success, JSON.toJSONString(result));
        return result;
    }

    private boolean isSuccess(Object result) {
        if (result instanceof Map<?, ?> map && map.get("code") instanceof Number code)
            return code.intValue() == 0 || code.intValue() == 1;
        return JSON.toJSONString(result).matches("(?s).*\\\"code\\\"\\s*:\\s*[01].*");
    }
}
