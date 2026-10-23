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

import java.util.ArrayList;
import java.util.List;

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

        // 유저를 잠가서 조회: 같은 유저가 동시에 저장해도 한 번에 하나씩 처리돼서 두 목록이 섞이지 않음
        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 1. 기존 질환 전부 삭제
        userConditionRepository.deleteAllByUser_Id(userId);
        // 같은 이름을 다시 저장할 때 중복 오류가 나지 않도록 삭제를 DB에 먼저 반영
        userConditionRepository.flush();

        // 2. 요청한 질환 저장
        List<UserCondition> saved = new ArrayList<>();
        for (String name : names) {
            UserCondition condition = UserCondition.builder()
                    .user(user)
                    .conditionName(name)
                    .build();
            saved.add(userConditionRepository.save(condition));
        }
        return toResponse(userId, saved);
    }

    // 질환 이름 정리: 앞뒤 공백 제거, 같은 이름은 한 번만 / 빈 이름, 70자 초과는 잘못된 요청
    private List<String> normalize(UserConditionsRequest request) {
        if (request == null || request.conditions() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<String> names = new ArrayList<>();
        for (String name : request.conditions()) {
            if (name == null || name.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }

            String trimmed = name.strip();
            if (trimmed.length() > MAX_NAME_LENGTH) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            if (!names.contains(trimmed)) {
                names.add(trimmed);
            }
        }
        return names;
    }

    private UserConditionsResponse toResponse(Long userId, List<UserCondition> conditions) {
        return new UserConditionsResponse(userId, conditions.stream()
                .map(UserCondition::getConditionName)
                .toList());
    }
}
