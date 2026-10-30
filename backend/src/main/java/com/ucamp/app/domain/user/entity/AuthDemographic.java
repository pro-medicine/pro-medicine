package com.ucamp.app.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "auth_demographics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthDemographic {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 가입 시점에 입력한 만 나이. 시간이 지나면 자동으로 증가하지 않음.
    @Column(name = "age")
    private Integer age;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "terms_agreed_at", nullable = false)
    private LocalDateTime termsAgreedAt;

    public AuthDemographic(User user, Integer age, String gender) {
        this.user = user;
        this.age = age;
        this.gender = gender;
        this.termsAgreedAt = LocalDateTime.now();
    }
}
