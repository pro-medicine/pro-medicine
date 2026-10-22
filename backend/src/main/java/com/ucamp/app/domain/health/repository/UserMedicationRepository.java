package com.ucamp.app.domain.health.repository;

import com.ucamp.app.domain.health.entity.UserMedication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserMedicationRepository extends JpaRepository<UserMedication, Long> {
    @Query("select m from UserMedication m left join fetch m.drug where m.user.id = :userId order by m.userMedicationId")
    List<UserMedication> findWithDrugByUserId(@Param("userId") Long userId);

    @Query("select max(m.updatedAt) from UserMedication m where m.user.id = :userId")
    LocalDateTime findLastUpdatedAt(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from UserMedication m where m.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
