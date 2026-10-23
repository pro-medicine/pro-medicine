package com.ucamp.app.domain.user.dto;

import com.ucamp.app.domain.user.entity.User;

import java.time.LocalDateTime;

// passwordHash는 응답에 포함하지 않음
public record UserResponse(
        Long userId,
        String email,
        String nickname,
        boolean shareIntakeStatus,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.isShareIntakeStatus(),
                user.getCreatedAt()
        );
    }
}
