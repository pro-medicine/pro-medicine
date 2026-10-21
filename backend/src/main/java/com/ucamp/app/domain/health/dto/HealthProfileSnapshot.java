package com.ucamp.app.domain.health.dto;

import com.ucamp.app.domain.health.entity.HealthProfile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 회원 건강 프로필을 한 번에 조회한 결과. profileValues 키는 health_profiles 컬럼명
public record HealthProfileSnapshot(
        boolean exists,
        Map<String, Object> profileValues,
        LocalDateTime updatedAt,
        List<String> conditions,
        List<MedicationInfo> medications
) {
    public static HealthProfileSnapshot empty() {
        return new HealthProfileSnapshot(false, Map.of(), null, List.of(), List.of());
    }

    public Object value(String profileKey) {
        return profileValues.get(profileKey);
    }

    public static Map<String, Object> valuesOf(HealthProfile profile) {
        Map<String, Object> map = new HashMap<>();
        for (String key : HealthProfile.PROFILE_KEYS) {
            Object v = profile.getSurveyValue(key);
            if (v != null) {
                map.put(key, v);
            }
        }
        return map;
    }
}
