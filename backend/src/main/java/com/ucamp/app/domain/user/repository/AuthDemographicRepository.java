package com.ucamp.app.domain.user.repository;

import com.ucamp.app.domain.user.entity.AuthDemographic;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthDemographicRepository extends JpaRepository<AuthDemographic, Long> {
}
