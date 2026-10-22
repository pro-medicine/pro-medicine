package com.ucamp.app.domain.health.service;

import com.ucamp.app.domain.health.dto.UserConditionsRequest;
import com.ucamp.app.domain.health.dto.UserConditionsResponse;
import com.ucamp.app.domain.health.entity.UserCondition;
import com.ucamp.app.domain.health.repository.UserConditionRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserConditionService {
    // user_conditions.condition_name은 VARCHAR(70)
    private static final int MAX_NAME_LENGTH = 70;

    private final UserConditionRepository userConditionRepository;
    private final UserRepository userRepository;

    public UserConditionsResponse getConditions(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toResponse(userId, userConditionRepository.findAllByUser_IdOrderByUserConditionIdAsc(userId));
    }

    // 기존 질환을 모두 지우고 요청 목록으로 교체 ("없음"은 빈 배열)
    @Transactional
    public UserConditionsResponse replaceConditions(Long userId, UserConditionsRequest request) {
        List<String> names = normalize(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        userConditionRepository.deleteAllByUser_Id(userId);
        // 같은 이름을 다시 저장할 때 (user_id, condition_name) 중복이 나지 않도록 삭제를 먼저 반영
        userConditionRepository.flush();

        List<UserCondition> saved = userConditionRepository.saveAll(names.stream()
                .map(name -> UserCondition.builder().user(user).conditionName(name).build())
                .toList());
        return toResponse(userId, saved);
    }

    // 앞뒤 공백 제거, 중복 제거(순서 유지), 빈 값이나 70자 초과는 잘못된 요청
    private List<String> normalize(UserConditionsRequest request) {
        if (request == null || request.conditions() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Set<String> names = new LinkedHashSet<>();
        for (String name : request.conditions()) {
            if (name == null || name.isBlank() || name.strip().length() > MAX_NAME_LENGTH) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            names.add(name.strip());
        }
        return List.copyOf(names);
    }

    private UserConditionsResponse toResponse(Long userId, List<UserCondition> conditions) {
        return new UserConditionsResponse(userId, conditions.stream()
                .map(UserCondition::getConditionName)
                .toList());
    }
}
