package com.ucamp.app.home.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

// intake_records 테이블 (Repository를 만들기 위한 최소 Entity)
// ddl-auto: none 이라 이 클래스가 테이블 구조를 바꾸지 않음
@Entity
@Table(name = "intake_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomeEntity {

    @Id
    @Column(name = "intake_record_id")   // AUTO_INCREMENT가 없어서 @GeneratedValue 안 씀
    private Long id;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "intake_date")
    private LocalDate intakeDate;

    private String status;
}