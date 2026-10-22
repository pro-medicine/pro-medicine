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
import java.util.Set;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "health_profiles")
@EntityListeners(AuditingEntityListener.class)
public class HealthProfile {

    // 설문 문항의 profile_key로 쓸 수 있는 컬럼명
    public static final String KEY_GENDER = "gender";
    public static final String KEY_BIRTH_YEAR = "birth_year";
    public static final String KEY_SMOKING = "smoking";
    public static final String KEY_DRINKING = "drinking";
    public static final String KEY_CAFFEINE = "caffeine";
    public static final String KEY_DIET_TYPE = "diet_type";
    public static final String KEY_SUN_EXPOSURE = "sun_exposure";
    public static final String KEY_HEIGHT = "height";
    public static final String KEY_WEIGHT = "weight";
    public static final Set<String> PROFILE_KEYS = Set.of(KEY_GENDER, KEY_BIRTH_YEAR, KEY_SMOKING, KEY_DRINKING,
            KEY_CAFFEINE, KEY_DIET_TYPE, KEY_SUN_EXPOSURE, KEY_HEIGHT, KEY_WEIGHT);

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

    public static HealthProfile create(User user) {
        return HealthProfile.builder().user(user).build();
    }

    // 설문 답을 profile_key 컬럼에 반영한다. 선택형은 value_code, 숫자형은 value_number를 저장
    public void applySurveyValue(String profileKey, String valueCode, BigDecimal valueNumber) {
        switch (profileKey) {
            case KEY_GENDER -> this.gender = valueCode;
            case KEY_SMOKING -> this.smoking = valueCode;
            case KEY_DRINKING -> this.drinking = valueCode;
            case KEY_CAFFEINE -> this.caffeine = valueCode;
            case KEY_DIET_TYPE -> this.dietType = valueCode;
            case KEY_SUN_EXPOSURE -> this.sunExposure = valueCode;
            case KEY_BIRTH_YEAR -> this.birthYear = valueNumber == null ? null : valueNumber.intValue();
            case KEY_HEIGHT -> this.height = valueNumber;
            case KEY_WEIGHT -> this.weight = valueNumber;
            default -> throw new IllegalArgumentException("unknown profile key: " + profileKey);
        }
    }

    // 설문 미리 채우기용. 선택형 컬럼은 value_code 문자열, 숫자형 컬럼은 BigDecimal
    public Object getSurveyValue(String profileKey) {
        return switch (profileKey) {
            case KEY_GENDER -> gender;
            case KEY_SMOKING -> smoking;
            case KEY_DRINKING -> drinking;
            case KEY_CAFFEINE -> caffeine;
            case KEY_DIET_TYPE -> dietType;
            case KEY_SUN_EXPOSURE -> sunExposure;
            case KEY_BIRTH_YEAR -> birthYear == null ? null : BigDecimal.valueOf(birthYear);
            case KEY_HEIGHT -> height;
            case KEY_WEIGHT -> weight;
            default -> null;
        };
    }
}
