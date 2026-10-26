package com.ucamp.app.domain.guardian.service;

import com.ucamp.app.domain.guardian.dto.GuardianResponse;
import com.ucamp.app.domain.guardian.entity.GuardianSetting;
import com.ucamp.app.domain.guardian.repository.GuardianSettingRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardianSettingService {

    private final GuardianSettingRepository guardianSettingRepository;
    private final UserRepository userRepository;

    @Transactional
    public GuardianResponse request(Long guardianUserId, Long targetId, int inactivityDays) {
        User guardian = userRepository.findById(guardianUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (guardianSettingRepository.existsByUserIdAndGuardianUserId(targetId, guardianUserId)) {
            throw new BusinessException(ErrorCode.ALREADY_GUARDIAN);
        }
        GuardianSetting saved = guardianSettingRepository.save(GuardianSetting.request(target, guardian, inactivityDays));
        // TODO: 안부지킴이 신청 알림
        return GuardianResponse.from(saved);
    }

    @Transactional
    public GuardianResponse accept(Long userId, Long settingId) {
        GuardianSetting setting = get(settingId);
        if (!setting.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        setting.accept();
        return GuardianResponse.from(setting);
    }

    @Transactional
    public void remove(Long userId, Long settingId) {
        GuardianSetting setting = get(settingId);
        boolean isParty = setting.getUser().getId().equals(userId)
                || setting.getGuardianUser().getId().equals(userId);
        if (!isParty) throw new BusinessException(ErrorCode.FORBIDDEN);
        // TODO: soft delete?
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
        return GuardianSettingRepository.findById(id)
                .orElseThrow(()->new BusinessException(ErrorCode.SETTING_NOT_FOUND));
    }
}