package com.ucamp.app.domain.follow.dto;

import jakarta.validation.constraints.NotBlank;

public record FollowCreateRequest(
        @NotBlank String followingNickname) {
}
