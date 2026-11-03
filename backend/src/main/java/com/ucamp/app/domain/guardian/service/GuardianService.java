package com.ucamp.app.domain.guardian.service;

import com.ucamp.app.domain.friend.enums.FollowStatus;
import com.ucamp.app.domain.friend.repository.FollowRepository;
import com.ucamp.app.domain.guardian.dto.GuardianResponse;
import com.ucamp.app.domain.guardian.entity.GuardianSetting;
import com.ucamp.app.domain.guardian.repository.GuardianSettingRepository;
import com.ucamp.app.domain.notification.event.GuardianAcceptedEvent;
import com.ucamp.app.domain.notification.event.GuardianRequestedEvent;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardianService {

    private final GuardianSettingRepository guardianSettingRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public GuardianResponse request(Long guardianUserId, Long targetId, int inactivityDays) {
        followRepository.findAllBetweenUsersForUpdate(guardianUserId, targetId);
        User guardian = userRepository.findById(guardianUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        boolean mutual = followRepository.existsByFollowerIdAndFollowingIdAndStatus(guardianUserId, targetId, FollowStatus.ACCEPTED)
                && followRepository.existsByFollowerIdAndFollowingIdAndStatus(targetId, guardianUserId, FollowStatus.ACCEPTED);
        if (!mutual) {
            throw new BusinessException(ErrorCode.NOT_FRIEND);
        }
        if (guardianSettingRepository.existsByUserIdAndGuardianUserId(targetId, guardianUserId)) {
            throw new BusinessException(ErrorCode.ALREADY_GUARDIAN);
        }
        GuardianSetting saved = guardianSettingRepository.save(GuardianSetting.request(target, guardian, inactivityDays));
        // 안부지킴이 신청 알림: 커밋된 뒤 알림 쪽(GuardianNotificationService)에서 받는 사람에게 알림을 만든다
        eventPublisher.publishEvent(new GuardianRequestedEvent(
                saved.getGuardianSettingId(), guardianUserId, targetId));
        return GuardianResponse.from(saved);
    }

    @Transactional
    public GuardianResponse accept(Long userId, Long settingId) {
        GuardianSetting setting = get(settingId);
        if (!setting.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        boolean alreadyActive = Boolean.TRUE.equals(setting.getIsActive());
        setting.accept();
        if (!alreadyActive) {
            // 안부지킴이 수락 알림: 처음 수락할 때만 신청한 사람에게 보낸다
            eventPublisher.publishEvent(new GuardianAcceptedEvent(
                    setting.getGuardianSettingId(),
                    setting.getGuardianUser().getId(),
                    userId,
                    setting.getInactivityDays()));
        }
        return GuardianResponse.from(setting);
    }

    @Transactional
    public void remove(Long userId, Long settingId) {
        GuardianSetting setting = get(settingId);
        boolean isParty = setting.getUser().getId().equals(userId)
                || setting.getGuardianUser().getId().equals(userId);
        if (!isParty) throw new BusinessException(ErrorCode.FORBIDDEN);
        guardianSettingRepository.delete(setting);
    }

    @Transactional
    public GuardianResponse changeDays(Long guardianUserId, Long settingId, int days) {
        GuardianSetting setting = get(settingId);
        if (!setting.getGuardianUser().getId().equals(guardianUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        setting.changeInactivityDays(days);
        return GuardianResponse.from(setting);
    }

    public List<GuardianResponse> getWatching(Long userId) {
        return guardianSettingRepository.findByGuardian(userId).stream()
                .map(GuardianResponse::from).toList();
    }

    public List<GuardianResponse> getGuardian(Long userId) {
        return guardianSettingRepository.findByProtectedUser(userId).stream()
                .map(GuardianResponse::from).toList();
    }

    private GuardianSetting get(Long id) {
        return guardianSettingRepository.findById(id)
                .orElseThrow(()->new BusinessException(ErrorCode.SETTING_NOT_FOUND));
    }

    public boolean existsBetween(Long a, Long b) {
        return guardianSettingRepository.existsByUserIdAndGuardianUserId(a, b)
                || guardianSettingRepository.existsByUserIdAndGuardianUserId(b, a);
    }
}