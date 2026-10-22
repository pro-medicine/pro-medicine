package com.ucamp.app.domain.friend.dto;

import jakarta.validation.constraints.NotBlank;

public record FollowCreateRequest(
        @NotBlank String nickname) {
}
