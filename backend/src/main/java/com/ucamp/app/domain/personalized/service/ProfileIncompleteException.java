package com.ucamp.app.domain.personalized.service;

import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.Getter;

import java.util.List;

// 건강정보가 부족해 V2를 시작할 수 없음 (PQ001). 응답 data에 빠진 항목(missing)을 담는다
@Getter
public class ProfileIncompleteException extends BusinessException {
    private final List<String> missing;

    public ProfileIncompleteException(List<String> missing) {
        super(ErrorCode.PERSONALIZED_PROFILE_INCOMPLETE);
        this.missing = List.copyOf(missing);
    }
}
