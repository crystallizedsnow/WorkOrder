package com.aiassistant.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具参数注解，描述参数含义。
 * 替代 dev.langchain4j.agent.tool.P，仅保留 value() 作为参数描述。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface P {

    String value();
}
