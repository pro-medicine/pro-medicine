package com.ucamp.app.domain.friend.dto;

import com.ucamp.app.domain.friend.enums.FollowStatus;
import jakarta.validation.constraints.NotNull;

public record FollowRespondRequest(
        @NotNull FollowStatus status
        ) {
}
