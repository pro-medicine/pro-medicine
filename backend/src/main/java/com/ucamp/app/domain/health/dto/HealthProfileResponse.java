package com.ucamp.app.domain.health.dto;

import com.ucamp.app.domain.health.entity.HealthProfile;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HealthProfileResponse(
        Long userId,
        Integer birthYear,
        String gender,
        BigDecimal height,
        BigDecimal weight,
        String smoking,
        String drinking,
        String caffeine,
        String dietType,
        String sunExposure,
        LocalDateTime updatedAt
) {
    public static HealthProfileResponse from(HealthProfile profile) {
        return new HealthProfileResponse(
                profile.getUserId(),
                profile.getBirthYear(),
                profile.getGender(),
                profile.getHeight(),
                profile.getWeight(),
                profile.getSmoking(),
                profile.getDrinking(),
                profile.getCaffeine(),
                profile.getDietType(),
                profile.getSunExposure(),
                profile.getUpdatedAt()
        );
    }

    // 아직 건강 정보를 입력하지 않은 유저
    public static HealthProfileResponse empty(Long userId) {
        return new HealthProfileResponse(userId, null, null, null, null, null, null, null, null, null, null);
    }
}
