package com.ucamp.app.domain.health.service;

import com.ucamp.app.domain.health.dto.DrugResponse;
import com.ucamp.app.domain.health.entity.Drug;
import com.ucamp.app.domain.health.repository.DrugRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DrugService {
    private final DrugRepository drugRepository;

    public List<DrugResponse> search(String keyword) {
        List<Drug> drugs = (keyword == null || keyword.isBlank())
                ? drugRepository.findTop20ByOrderByNameAsc()
                : drugRepository.findTop20ByNameContainingOrderByNameAsc(keyword.strip());
        return drugs.stream().map(DrugResponse::from).toList();
    }

    public Map<String, Drug> findByIds(Collection<String> drugIds) {
        if (drugIds.isEmpty()) {
            return Map.of();
        }
        return drugRepository.findAllById(drugIds).stream()
                .collect(Collectors.toMap(Drug::getDrugId, Function.identity()));
    }
}
