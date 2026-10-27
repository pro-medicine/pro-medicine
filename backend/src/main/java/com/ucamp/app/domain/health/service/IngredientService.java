package com.ucamp.app.domain.health.service;

import com.ucamp.app.domain.health.dto.IngredientResponse;
import com.ucamp.app.domain.health.entity.IngredientCode;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

// 설문·추천에서 쓰는 성분 코드 목록 (코드 → 이름·단위)
@Service
public class IngredientService {

    public List<IngredientResponse> list() {
        return Arrays.stream(IngredientCode.values()).map(IngredientResponse::from).toList();
    }
}
