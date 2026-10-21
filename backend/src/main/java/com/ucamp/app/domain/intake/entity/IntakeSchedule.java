package com.ucamp.app.domain.intake.entity;

import com.ucamp.app.domain.product.entity.UserProduct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "intake_schedules")
@EntityListeners(AuditingEntityListener.class)
public class IntakeSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_product_id", nullable = false)
    private UserProduct userProduct;

    // TODO: enum 전환
    @Column(name = "meal_slot", nullable = false, length = 15)
    private String mealSlot;

    // TODO: enum 전환
    @Column(name = "meal_relation", nullable = false, length = 10)
    private String mealRelation;

    @Column(name = "remind_at", nullable = false)
    private LocalTime remindAt;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public IntakeSchedule(UserProduct userProduct, String mealSlot, String mealRelation, LocalTime remindAt,
                          Boolean isEnabled) {
        this.userProduct = userProduct;
        this.mealSlot = mealSlot;
        this.mealRelation = mealRelation;
        this.remindAt = remindAt;
        this.isEnabled = isEnabled != null ? isEnabled : true;
    }
}
