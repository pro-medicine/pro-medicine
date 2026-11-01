package com.ucamp.app.domain.calendar.entity;

import com.ucamp.app.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "prescriptions")
public class UserPrescription {

    @Id
    @Column(name = "prescription_id", length = 30)
    private Long prescriptionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "start_at")
    private LocalDate startAt;

    @Column(name = "end_at")
    private LocalDate endAt;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "is_shown")
    private boolean isShown;
}
