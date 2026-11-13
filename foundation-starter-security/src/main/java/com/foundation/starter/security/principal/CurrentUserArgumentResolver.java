package com.foundation.starter.security.principal;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Optional;

/**
 * 控制器注入当前人员（F-07 §2）：支持 {@link Principal} 与 {@code Optional<Principal>} 参数类型。
 *
 * <p>由 {@code foundation-starter-web} 或本 starter 的 web 配置注册（引入 spring-webmvc 时）。
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        if (Principal.class.isAssignableFrom(type)) {
            return true;
        }
        return Optional.class.isAssignableFrom(type)
                && Principal.class.isAssignableFrom(parameter.getGenericParameterType() instanceof java.lang.reflect.ParameterizedType pt
                ? (Class<?>) pt.getActualTypeArguments()[0]
                : Object.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Optional<Principal> current = PrincipalHolder.current();
        if (Optional.class.isAssignableFrom(parameter.getParameterType())) {
            return current;
        }
        return current.orElseThrow(() -> new com.foundation.common.api.error.BizException(
                com.foundation.common.api.error.ErrorCode.UNAUTHORIZED));
    }
}
