package com.ucamp.app.domain.user.service;

import com.ucamp.app.domain.user.dto.UserResponse;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;

    public UserResponse getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse changeNickname(Long userId, String nickname) {
        // users.nickname은 VARCHAR(50)
        if (nickname == null || nickname.isBlank() || nickname.strip().length() > 50) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String trimmed = nickname.strip();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (trimmed.equals(user.getNickname())) {
            return UserResponse.from(user);
        }
        if (userRepository.existsByNickname(trimmed)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        user.changeNickname(trimmed);
        try {
            // 동시에 같은 닉네임으로 변경한 경우 DB UNIQUE 제약 위반을 여기서 잡음
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        return UserResponse.from(user);
    }

    @Transactional
    public void markActive(Long userId) {
        // 안부지킴이 알림용
        LocalDateTime now = LocalDateTime.now();
        userRepository.touchActive(userId, now, now.minusMinutes(10));
    }
}
