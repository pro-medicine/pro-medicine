package com.ucamp.app.domain.medicine.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "medicine_products")
public class MedicineProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "medicine_id")
    private Long medicineId;

    @Column(name = "name_ko", nullable = false, length = 150)
    private String nameKo;

    @Column(name = "display_name", length = 30)
    private String displayName;

    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "item_seq", length = 20)
    private String itemSeq;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "search_keywords", length = 300)
    private String searchKeywords;

    public String shownName() {
        return displayName != null ? displayName : nameKo;
    }
}
