package com.aiassistant.memory;

import com.aiassistant.llm.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenEstimatorTest {
    private final TokenEstimator estimator = new TokenEstimator();

    @Test void chineseIsNotEstimatedAsOneTokenPerFourCharacters() {
        assertTrue(estimator.text("这是一个中文上下文预算测试") >= 10);
    }

    @Test void messageIncludesProtocolOverhead() {
        assertTrue(estimator.message(ChatMessage.user("hello")) > estimator.text("hello"));
    }
}
