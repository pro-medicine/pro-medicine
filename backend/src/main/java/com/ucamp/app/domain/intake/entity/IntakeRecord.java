package com.ucamp.app.domain.intake.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "intake_records")
public class IntakeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "intake_record_id")
    private Long intakeRecordId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private IntakeSchedule schedule;

    @Column(name = "intake_date", nullable = false)
    private LocalDate intakeDate;

    // TODO: enum 전환
    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "proof_image_url", length = 1000)
    private String proofImageUrl;

    @Column(name = "is_shared", nullable = false)
    private Boolean isShared;

    @Builder
    public IntakeRecord(IntakeSchedule schedule, LocalDate intakeDate, String status, LocalDateTime completedAt,
                        String proofImageUrl, Boolean isShared) {
        this.schedule = schedule;
        this.intakeDate = intakeDate;
        this.status = status;
        this.completedAt = completedAt;
        this.proofImageUrl = proofImageUrl;
        this.isShared = isShared != null ? isShared : false;
    }
}
