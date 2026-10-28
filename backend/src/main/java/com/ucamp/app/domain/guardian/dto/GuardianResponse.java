package com.ucamp.app.domain.guardian.dto;

import com.ucamp.app.domain.guardian.entity.GuardianSetting;

import java.time.LocalDateTime;

public record GuardianResponse(
        Long guardianSettingId,
        Long userId,
        String nickname,
        Long guardianUserId,
        String guardianNickname,
        int inactivityDays,
        boolean active,
        LocalDateTime consentedAt
) {
    public static GuardianResponse from(GuardianSetting g) {
        return new GuardianResponse(
                g.getGuardianSettingId(),
                g.getUser().getId(),
                g.getUser().getNickname(),
                g.getGuardianUser().getId(),
                g.getGuardianUser().getNickname(),
                g.getInactivityDays(),
                g.getIsActive(),
                g.getConsentedAt()
        );
    }
}
