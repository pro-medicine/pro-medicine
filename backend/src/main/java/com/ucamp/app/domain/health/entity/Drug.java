package com.ucamp.app.domain.health.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "drugs")
public class Drug {

    @Id
    @Column(name = "drug_id", length = 50)
    private String drugId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "drug_class", nullable = false, length = 50)
    private String drugClass;

    @Builder
    public Drug(String drugId, String name, String drugClass) {
        this.drugId = drugId;
        this.name = name;
        this.drugClass = drugClass;
    }
}
