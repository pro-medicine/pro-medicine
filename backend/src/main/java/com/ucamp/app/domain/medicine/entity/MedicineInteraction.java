package com.ucamp.app.domain.medicine.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "medicine_interactions")
public class MedicineInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interaction_id")
    private Long interactionId;

    @Column(name = "active_code", nullable = false, length = 40)
    private String activeCode;

    @Column(name = "supplement_ingredient_code", nullable = false, length = 30)
    private String supplementIngredientCode;

    /** AVOID, CAUTION, TIMING, INFO */
    @Column(name = "severity", nullable = false, length = 10)
    private String severity;

    @Column(name = "message_ko", nullable = false, length = 300)
    private String messageKo;

    @Column(name = "source_id", nullable = false, length = 20)
    private String sourceId;

    @Column(name = "active", nullable = false)
    private boolean active;
}
