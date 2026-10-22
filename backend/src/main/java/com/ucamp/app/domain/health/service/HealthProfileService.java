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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthProfileService {
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

        HealthProfile profile = healthProfileRepository.findById(userId).orElse(null);
        if (profile == null) {
            profile = healthProfileRepository.save(HealthProfile.builder()
                    .user(user)
                    .birthYear(request.birthYear())
                    .gender(request.gender())
                    .height(request.height())
                    .weight(request.weight())
                    .smoking(request.smoking())
                    .drinking(request.drinking())
                    .caffeine(request.caffeine())
                    .dietType(request.dietType())
                    .sunExposure(request.sunExposure())
                    .build());
        } else {
            profile.update(request.birthYear(), request.gender(), request.height(), request.weight(),
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
                || !isPositive(request.height())
                || !isPositive(request.weight())
                || isTooLong(request.gender(), 10)
                || isTooLong(request.smoking(), 20)
                || isTooLong(request.drinking(), 20)
                || isTooLong(request.caffeine(), 20)
                || isTooLong(request.dietType(), 20)
                || isTooLong(request.sunExposure(), 20)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean isPositive(BigDecimal value) {
        return value == null || value.signum() > 0;
    }

    private boolean isTooLong(String value, int max) {
        return value != null && value.length() > max;
    }
}
