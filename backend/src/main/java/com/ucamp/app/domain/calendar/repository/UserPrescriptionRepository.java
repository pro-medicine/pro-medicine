package com.ucamp.app.domain.calendar.repository;

import com.ucamp.app.domain.calendar.entity.UserPrescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPrescriptionRepository extends JpaRepository<UserPrescription, Long> {
    List<UserPrescription> findAllByUserId(Long userId);
}
