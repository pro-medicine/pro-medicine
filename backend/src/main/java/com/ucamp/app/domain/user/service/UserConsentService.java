package com.ucamp.app.domain.user.service;

import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 설문(AI 추천) 기능에서 쓰는 사용자 조회와 민감정보 동의. 기존 UserService는 건드리지 않으려고 따로 둔다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserConsentService {
    private final UserRepository userRepository;

    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public User agreeSensitiveConsent(Long userId) {
        User user = getUser(userId);
        user.agreeSensitiveConsent();
        return user;
    }
}
