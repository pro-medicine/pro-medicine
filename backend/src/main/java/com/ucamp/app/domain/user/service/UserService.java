package com.ucamp.app.domain.user.service;

import com.ucamp.app.domain.user.dto.NicknameCheckResponse;
import com.ucamp.app.domain.user.dto.UserResponse;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public NicknameCheckResponse checkNickname(String nickname) {
        // users.nickname은 VARCHAR(50)
        if (nickname == null || nickname.isBlank() || nickname.strip().length() > 50) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String trimmed = nickname.strip();
        return new NicknameCheckResponse(trimmed, !userRepository.existsByNickname(trimmed));
    }
}
