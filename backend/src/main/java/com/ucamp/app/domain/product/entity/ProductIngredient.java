package com.ucamp.app.domain.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product_ingredients")
public class ProductIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_ingredient_id")
    private Long productIngredientId;

    @Column(name = "catalog_product_id", nullable = false)
    private Long catalogProductId;

    /** IngredientCode 24개 또는 OTHER */
    @Column(name = "ingredient_code", nullable = false, length = 30)
    private String ingredientCode;

    @Column(name = "ingredient_name", nullable = false, length = 100)
    private String ingredientName;
}
