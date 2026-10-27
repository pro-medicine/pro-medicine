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

    // PRODUCT
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "영양제를 찾을 수 없습니다."),
    PRODUCT_ALREADY_ADDED(HttpStatus.CONFLICT, "P002", "이미 등록한 영양제입니다."),
    USER_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "P003", "등록한 영양제를 찾을 수 없습니다."),

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
