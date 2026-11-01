package com.ucamp.app.domain.prescription.repository;

import com.ucamp.app.domain.prescription.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
    List<Prescription> findAllByUserId(Long userId);

    Optional<Prescription> findByPrescriptionIdAndUserId(Long prescriptionId, Long userId);
}
