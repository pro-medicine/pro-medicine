package com.ucamp.app.domain.recommendation.service;

import com.ucamp.app.domain.user.entity.AuthDemographic;
import com.ucamp.app.domain.user.repository.AuthDemographicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * auth_demographics 테이블에서 가입 시 입력한 성별·나이를 조회해
 * health_profiles에 아직 없을 때 폴백으로 쓴다 (읽기 전용).
 * 우선순위: 설문 답 > health_profiles > auth_demographics.
 */
@Component
@RequiredArgsConstructor
public class MemberDemographicsFallback {
    private final AuthDemographicRepository authDemographicRepository;

    /** 가입 성별을 프로필 코드(F/M)로 변환. '선택 안 함'이나 행 없음은 null */
    public String resolveGenderCode(Long userId) {
        return authDemographicRepository.findById(userId)
                .map(AuthDemographic::getGender)
                .map(MemberDemographicsFallback::toGenderCode)
                .orElse(null);
    }

    /** 가입 나이 + 동의 시각으로 출생 연도 추정. 행 없음이나 나이 null이면 null */
    public Integer resolveBirthYear(Long userId) {
        return authDemographicRepository.findById(userId)
                .filter(ad -> ad.getAge() != null && ad.getTermsAgreedAt() != null)
                .map(ad -> ad.getTermsAgreedAt().getYear() - ad.getAge())
                .orElse(null);
    }

    static String toGenderCode(String gender) {
        if (gender == null) return null;
        return switch (gender) {
            case "여성" -> "F";
            case "남성" -> "M";
            default -> null;
        };
    }
}
