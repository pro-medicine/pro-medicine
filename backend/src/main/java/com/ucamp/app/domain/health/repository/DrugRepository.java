package com.ucamp.app.domain.health.repository;

import com.ucamp.app.domain.health.entity.Drug;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DrugRepository extends JpaRepository<Drug, String> {
    List<Drug> findTop20ByNameContainingOrderByNameAsc(String keyword);

    List<Drug> findTop20ByOrderByNameAsc();
}
