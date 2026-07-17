package com.carolcoral.apiserver.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置
 * <p>
 * 处理 Hibernate 懒加载代理序列化问题，避免 ByteBuddyInterceptor 序列化异常
 * </p>
 *
 * @author carolcoral
 */
@Configuration
public class JacksonConfig {

    /**
     * 自定义 Jackson ObjectMapper
     * 禁用空 Bean 序列化失败，使 Hibernate 代理对象可以正常序列化
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder.featuresToDisable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
}
