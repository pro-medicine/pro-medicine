package com.ucamp.app.domain.medicine.repository;

import com.ucamp.app.domain.medicine.entity.MedicineProduct;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MedicineProductRepository extends JpaRepository<MedicineProduct, Long> {
    List<MedicineProduct> findByActiveTrueOrderByMedicineIdAsc();

    @Query("""
        select m from MedicineProduct m
        where m.active = true
          and (
            lower(m.nameKo) like lower(concat('%', :q, '%'))
            or lower(coalesce(m.displayName, '')) like lower(concat('%', :q, '%'))
          )
        order by m.medicineId asc
        """)
    List<MedicineProduct> searchActive(
            @Param("q") String q,
            Pageable pageable
    );
}
