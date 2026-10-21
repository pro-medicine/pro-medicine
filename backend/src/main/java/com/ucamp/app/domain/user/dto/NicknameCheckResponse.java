package com.ucamp.app.domain.user.dto;

public record NicknameCheckResponse(
        String nickname,
        boolean available
) {
}
