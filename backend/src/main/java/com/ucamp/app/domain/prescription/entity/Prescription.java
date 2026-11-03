package com.ucamp.app.domain.prescription.entity;

import com.ucamp.app.domain.prescription.enums.PrescriptionStatus;
import com.ucamp.app.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "prescriptions")
@EntityListeners(AuditingEntityListener.class)
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prescription_id")
    private Long prescriptionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "start_at")
    private LocalDate startAt;

    @Column(name = "end_at")
    private LocalDate endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrescriptionStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "medicine_id")
    private Long medicineId;

    @Builder
    private Prescription(Long userId, String name, Long medicineId, LocalDate startAt, LocalDate endAt) {
        this.userId = userId;
        this.name = name;
        this.medicineId = medicineId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = PrescriptionStatus.ACTIVE;
    }

    public boolean isActiveOn(LocalDate date) {
        return status == PrescriptionStatus.ACTIVE && !date.isBefore(startAt) && (endAt == null || !date.isAfter(endAt));
    }

    public void update(String name, Long medicineId, LocalDate startAt, LocalDate endAt) {
        this.name = name;
        this.medicineId = medicineId;
        this.startAt = startAt;
        this.endAt = endAt;
    }
}
