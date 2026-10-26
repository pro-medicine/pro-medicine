package com.ucamp.app.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E001", "서버 에러가 발생했습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "E002", "잘못된 요청입니다."),
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
    SURVEY_RESPONSE_NOT_FOUND(HttpStatus.NOT_FOUND, "S005", "설문 응답을 찾을 수 없습니다."),

    // RECOMMENDATION
    RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "추천 결과를 찾을 수 없습니다."),
    RECOMMENDATION_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "R002", "추천 요청이 올바르지 않습니다."),
    RECOMMENDATION_LLM_FAILED(HttpStatus.BAD_GATEWAY, "R003", "AI 추천을 만들지 못했습니다. 잠시 후 다시 시도해 주세요."),
    RECOMMENDATION_UNKNOWN_PIPELINE(HttpStatus.INTERNAL_SERVER_ERROR, "R004", "설정된 추천 파이프라인 버전이 없습니다."),

    // PERSONALIZED (AI 추천 V2 맞춤 질문). P001은 PRODUCT가 쓰고 있어 PQ 접두사를 쓴다
    PERSONALIZED_PROFILE_INCOMPLETE(HttpStatus.CONFLICT, "PQ001", "건강정보가 부족해 기본 설문으로 진행합니다."),
    PERSONALIZED_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "PQ002", "맞춤 질문 회차를 찾을 수 없습니다."),
    PERSONALIZED_INVALID_STATE(HttpStatus.CONFLICT, "PQ003", "지금은 이 요청을 처리할 수 없는 회차 상태입니다."),
    PERSONALIZED_INVALID_ANSWER(HttpStatus.BAD_REQUEST, "PQ004", "답변이 올바르지 않습니다."),
    PERSONALIZED_QUESTION_FAILED(HttpStatus.BAD_GATEWAY, "PQ005", "잠시 후 다시 시도해 주세요."),

    // PRODUCT
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "영양제를 찾을 수 없습니다."),

    // NOTIFICATION
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없습니다."),
  
    // FOLLOW
    INVALID_FOLLOW(HttpStatus.BAD_REQUEST, "F001", "본인에게는 팔로우 요청을 보낼 수 없습니다."),
    COMPLETED_FOLLOW_REQUEST(HttpStatus.BAD_REQUEST, "F002", "이미 처리된 팔로우 요청입니다."),
    ALREADY_FOLLOWING(HttpStatus.CONFLICT, "F003", "이미 팔로우 중입니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "F004", "유효하지 않은 요청입니다."),
    FOLLOWING_NOT_FOUND(HttpStatus.NOT_FOUND, "F005", "팔로잉을 찾을 수 없습니다."),
    NOT_FRIEND(HttpStatus.BAD_REQUEST, "F006", "친구 상태가 아닙니다."),

    // DISPOSAL
    DISPOSAL_INVALID_QUERY(HttpStatus.BAD_REQUEST, "D001", "검색어는 1~100자로 입력해주세요."),
    DISPOSAL_KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, "D002", "카카오 장소 검색 API 호출에 실패했습니다."),
    DISPOSAL_INVALID_LOCATION(HttpStatus.BAD_REQUEST, "D003", "올바른 위치 좌표와 검색 반경(500/1000/2000/3000m)을 입력해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
