package com.ucamp.app.global.auth;

import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

// TEMP: 로그인 연동 시 교체
// 로그인이 아직 없어서 X-User-Id 헤더로 회원을 식별한다. 헤더가 없으면 비회원(null).
@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return resolve(webRequest.getHeader(HEADER));
    }

    public Long resolve(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(headerValue.strip());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
