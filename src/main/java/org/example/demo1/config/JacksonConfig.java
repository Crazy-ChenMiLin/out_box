package org.example.demo1.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 配置。
 *
 * 业务代码用 ObjectMapper 把 outbox payload 转成 JSON 字符串。
 */
@Configuration
public class JacksonConfig {

    // 如果 Spring 容器里还没有 ObjectMapper，就创建一个默认的。
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
