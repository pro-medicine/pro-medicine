package com.ucamp.app.domain.guardian.repository;

import com.ucamp.app.domain.guardian.entity.GuardianSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianSettingRepository extends JpaRepository<GuardianSetting, Long> {
}
