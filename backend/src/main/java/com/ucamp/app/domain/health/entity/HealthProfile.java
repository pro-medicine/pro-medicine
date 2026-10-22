package com.ucamp.app.domain.health.entity;

import com.ucamp.app.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "health_profiles")
@EntityListeners(AuditingEntityListener.class)
public class HealthProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "height", precision = 5, scale = 1)
    private BigDecimal height;

    @Column(name = "weight", precision = 5, scale = 1)
    private BigDecimal weight;

    @Column(name = "smoking", length = 20)
    private String smoking;

    @Column(name = "drinking", length = 20)
    private String drinking;

    @Column(name = "caffeine", length = 20)
    private String caffeine;

    @Column(name = "diet_type", length = 20)
    private String dietType;

    @Column(name = "sun_exposure", length = 20)
    private String sunExposure;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public HealthProfile(User user, Integer birthYear, String gender, BigDecimal height, BigDecimal weight,
                         String smoking, String drinking, String caffeine, String dietType, String sunExposure) {
        this.user = user;
        this.birthYear = birthYear;
        this.gender = gender;
        this.height = height;
        this.weight = weight;
        this.smoking = smoking;
        this.drinking = drinking;
        this.caffeine = caffeine;
        this.dietType = dietType;
        this.sunExposure = sunExposure;
    }
}
