package com.ucamp.app.domain.health.repository;

import com.ucamp.app.domain.health.entity.HealthProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthProfileRepository extends JpaRepository<HealthProfile, Long> {
}
