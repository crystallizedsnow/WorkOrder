package com.example.workorder.cli.interceptor;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Random;

@Component
public class TraceIdInterceptor implements HandlerInterceptor {

    public static final String TRACE_ID_HEADER = "X-Trace-ID";
    public static final String TRACE_ID_KEY = "traceId";

    private static final Random RANDOM = new Random();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generate18DigitTraceId();
        }
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove(TRACE_ID_KEY);
    }

    private String generate18DigitTraceId() {
        long min = 100000000000000000L;
        long max = 999999999999999999L;
        long randomNum = min + RANDOM.nextLong(max - min + 1);
        return String.valueOf(randomNum);
    }
}