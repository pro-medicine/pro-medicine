package com.ucamp.app.domain.personalized.controller;

import com.ucamp.app.domain.personalized.service.ProfileIncompleteException;
import com.ucamp.app.global.exception.ErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * V2 컨트롤러 전용: PQ001 응답에 빠진 건강정보(missing)를 담는다. 나머지 오류는 공통 GlobalExceptionHandler가 처리한다.
 * 공통 ApiResponse는 오류에 data를 담지 않으므로 같은 모양({ success, code, message, data })을 여기서 만든다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PersonalizedSessionController.class)
public class PersonalizedExceptionHandler {

    public record ErrorBody(boolean success, String code, String message, MissingData data) {
    }

    public record MissingData(List<String> missing) {
    }

    @ExceptionHandler(ProfileIncompleteException.class)
    public ResponseEntity<ErrorBody> profileIncomplete(ProfileIncompleteException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(new ErrorBody(false, code.getCode(), code.getMessage(), new MissingData(ex.getMissing())));
    }
}
