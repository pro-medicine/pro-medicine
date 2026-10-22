package com.ucamp.app.domain.health.repository;

import com.ucamp.app.domain.health.entity.UserCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserConditionRepository extends JpaRepository<UserCondition, Long> {
    List<UserCondition> findByUser_IdOrderByUserConditionIdAsc(Long userId);

    @Query("select max(c.createdAt) from UserCondition c where c.user.id = :userId")
    LocalDateTime findLastCreatedAt(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from UserCondition c where c.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
