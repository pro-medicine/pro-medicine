package com.ucamp.app.domain.health.repository;

import com.ucamp.app.domain.health.entity.UserCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserConditionRepository extends JpaRepository<UserCondition, Long> {
    List<UserCondition> findAllByUser_IdOrderByUserConditionIdAsc(Long userId);

    void deleteAllByUser_Id(Long userId);
}
