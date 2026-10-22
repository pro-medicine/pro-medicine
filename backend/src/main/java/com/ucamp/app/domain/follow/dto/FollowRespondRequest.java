package com.ucamp.app.domain.follow.dto;

import com.ucamp.app.domain.follow.enums.FollowStatus;
import jakarta.validation.constraints.NotNull;

public record FollowRespondRequest(
        @NotNull FollowStatus status
        ) {
}
