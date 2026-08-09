package com.aiassistant.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具方法注解，标记可被 LLM 调用的工具。
 * 替代 dev.langchain4j.agent.tool.Tool，仅保留 value() 作为工具描述。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {

    String[] value();
}
