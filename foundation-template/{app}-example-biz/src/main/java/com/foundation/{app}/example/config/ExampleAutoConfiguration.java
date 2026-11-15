package com.foundation.{app}.example.config;

import com.foundation.{app}.example.api.ExampleApi;
import com.foundation.{app}.example.api.ExampleController;
import com.foundation.{app}.example.application.ExampleService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 示例模块自动装配（BAS-25）。
 *
 * <p>范式与仓库既有模块一致：{@code @AutoConfiguration} + 逐 Bean {@link ConditionalOnMissingBean}，
 * 允许下游覆盖。控制器以 {@code @Bean} 装配，故本类不标 {@code @RestController}。
 *
 * <p>本示例不落库，故不依赖任何持久化 Bean（不加 DB 相关的 {@code @ConditionalOnBean}）。
 */
@AutoConfiguration
public class ExampleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ExampleApi exampleApi() {
        return new ExampleService();
    }

    @Bean
    @ConditionalOnMissingBean
    ExampleController exampleController(ExampleApi exampleApi) {
        return new ExampleController(exampleApi);
    }
}
