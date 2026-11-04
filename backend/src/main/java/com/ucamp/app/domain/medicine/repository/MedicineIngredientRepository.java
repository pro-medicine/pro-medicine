package com.ucamp.app.domain.medicine.repository;

import com.ucamp.app.domain.medicine.entity.MedicineIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicineIngredientRepository extends JpaRepository<MedicineIngredient, MedicineIngredient.Pk> {
    List<MedicineIngredient> findByMedicineId(Long medicineId);
}
