package com.ucamp.app.domain.medicine.repository;

import com.ucamp.app.domain.medicine.entity.MedicineInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MedicineInteractionRepository extends JpaRepository<MedicineInteraction, Long> {
    @Query("""
            select i from MedicineInteraction i
            where i.active = true
              and i.activeCode in :activeCodes
              and i.supplementIngredientCode in :supplementCodes
            """)
    List<MedicineInteraction> findActiveMatches(@Param("activeCodes") Collection<String> activeCodes,
                                                @Param("supplementCodes") Collection<String> supplementCodes);
}
