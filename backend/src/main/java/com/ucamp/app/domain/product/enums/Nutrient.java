package com.ucamp.app.domain.product.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 나의 영양관리에서 보여주는 성분과 성인 하루 권장량
// 한국인 영양소 섭취기준(2020) 성인 값을 참고한 화면 시안 값 (성별·나이 구분은 아직 안 함)
@Getter
@RequiredArgsConstructor
public enum Nutrient {
    VITAMIN_C("비타민 C", "mg", 100),
    VITAMIN_D("비타민 D", "㎍", 10),
    MAGNESIUM("마그네슘", "mg", 350),
    CALCIUM("칼슘", "mg", 700),
    // 오메가3(EPA+DHA)는 섭취기준에 없어 식약처 기능성 최소량 500mg 사용
    OMEGA3("오메가3", "mg", 500);

    private final String displayName;
    private final String unit;
    private final int recommendedAmount;
}
