package com.ucamp.app.domain.health.service;

import com.ucamp.app.domain.health.dto.HealthProfileRequest;
import com.ucamp.app.domain.health.dto.HealthProfileResponse;
import com.ucamp.app.domain.health.entity.HealthProfile;
import com.ucamp.app.domain.health.repository.HealthProfileRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthProfileService {
    private static final BigDecimal MAX_MEASURE = new BigDecimal("9999.9");

    private final HealthProfileRepository healthProfileRepository;
    private final UserRepository userRepository;

    public HealthProfileResponse getHealthProfile(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return healthProfileRepository.findById(userId)
                .map(HealthProfileResponse::from)
                .orElseGet(() -> HealthProfileResponse.empty(userId));
    }

    // 건강 정보가 없으면 새로 만들고, 있으면 전체 항목을 요청 값으로 바꿈
    @Transactional
    public HealthProfileResponse saveHealthProfile(Long userId, HealthProfileRequest request) {
        validate(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 저장 응답과 이후 조회 값이 같도록 DB와 같은 소수점 1자리로 맞춤 (170.50 → 170.5)
        BigDecimal height = toMeasureScale(request.height());
        BigDecimal weight = toMeasureScale(request.weight());

        HealthProfile profile = healthProfileRepository.findById(userId).orElse(null);
        if (profile == null) {
            profile = healthProfileRepository.save(HealthProfile.builder()
                    .user(user)
                    .birthYear(request.birthYear())
                    .gender(request.gender())
                    .height(height)
                    .weight(weight)
                    .smoking(request.smoking())
                    .drinking(request.drinking())
                    .caffeine(request.caffeine())
                    .dietType(request.dietType())
                    .sunExposure(request.sunExposure())
                    .build());
        } else {
            profile.update(request.birthYear(), request.gender(), height, weight,
                    request.smoking(), request.drinking(), request.caffeine(), request.dietType(), request.sunExposure());
        }
        // updatedAt을 응답에 담기 위해 바로 반영
        healthProfileRepository.flush();
        return HealthProfileResponse.from(profile);
    }

    // health_profiles CHECK 제약과 컬럼 길이에 맞춤
    private void validate(HealthProfileRequest request) {
        if (request == null
                || (request.birthYear() != null && (request.birthYear() < 1900 || request.birthYear() > 2100))
                || isInvalidMeasure(request.height())
                || isInvalidMeasure(request.weight())
                || isTooLong(request.gender(), 10)
                || isTooLong(request.smoking(), 20)
                || isTooLong(request.drinking(), 20)
                || isTooLong(request.caffeine(), 20)
                || isTooLong(request.dietType(), 20)
                || isTooLong(request.sunExposure(), 20)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // validate()를 통과한 값만 들어오므로 반올림 없이 자릿수만 맞춰짐 (반올림이 필요하면 예외)
    private BigDecimal toMeasureScale(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.UNNECESSARY);
    }

    // height, weight는 DECIMAL(5,1): 0 초과, 소수점 아래 1자리 이하, 최대 9999.9
    private boolean isInvalidMeasure(BigDecimal value) {
        if (value == null) {
            return false;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() <= 0
                || normalized.scale() > 1
                || normalized.compareTo(MAX_MEASURE) > 0;
    }

    private boolean isTooLong(String value, int max) {
        return value != null && value.length() > max;
    }
}
