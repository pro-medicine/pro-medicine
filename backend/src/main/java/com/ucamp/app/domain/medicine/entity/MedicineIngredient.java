package com.ucamp.app.domain.medicine.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "medicine_ingredients")
@IdClass(MedicineIngredient.Pk.class)
public class MedicineIngredient {

    @Id
    @Column(name = "medicine_id")
    private Long medicineId;

    @Id
    @Column(name = "active_code", length = 40)
    private String activeCode;

    @Column(name = "active_name_ko", nullable = false, length = 100)
    private String activeNameKo;

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Pk implements Serializable {
        private Long medicineId;
        private String activeCode;
    }
}
