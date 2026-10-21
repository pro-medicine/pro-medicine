package com.ucamp.app.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E001", "서버 에러가 발생했습니다."),
    INVALID_INPUT(HttpStatus.BAD_GATEWAY, "E002", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "E003", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "E004", "접근이 거부되었습니다."),

    // USER
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "U002", "이미 사용 중인 닉네임입니다."),

    // SURVEY
    SURVEY_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "진행 중인 설문을 찾을 수 없습니다."),
    SURVEY_INVALID_ANSWER(HttpStatus.BAD_REQUEST, "S002", "설문 답변이 올바르지 않습니다."),
    SURVEY_SENSITIVE_GUEST(HttpStatus.BAD_REQUEST, "S003", "비회원은 민감 문항에 답할 수 없습니다."),
    SURVEY_SENSITIVE_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "S004", "민감정보 수집 동의가 필요합니다."),
    SURVEY_RESPONSE_NOT_FOUND(HttpStatus.NOT_FOUND, "S005", "설문 응답을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
