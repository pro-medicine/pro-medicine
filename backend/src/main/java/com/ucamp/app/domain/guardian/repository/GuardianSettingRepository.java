package com.ucamp.app.domain.guardian.repository;

import com.ucamp.app.domain.guardian.entity.GuardianSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuardianSettingRepository extends JpaRepository<GuardianSetting, Long> {

    boolean existsByUserIdAndGuardianUserId(Long userId, Long guardianUserId);

    @Query("select g from GuardianSetting g join fetch g.user " +
            "where g.guardianUser.id = :guardianId order by g.guardianSettingId desc")
    List<GuardianSetting> findByGuardian(@Param("guardianId") Long guardianId);

    @Query("select g from GuardianSetting g join fetch g.guardianUser " +
            "where g.user.id = :userId order by g.guardianSettingId desc")
    List<GuardianSetting> findByProtectedUser(@Param("userId") Long userId);

    @Query("select g from GuardianSetting g " +
            "join fetch g.user u join fetch g.guardianUser " +
            "where g.isActive = true")
    List<GuardianSetting> findAllActiveWithUsers();
}
