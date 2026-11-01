package com.ucamp.app.domain.product.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 나의 영양관리에서 보여주는 성분과 성인 하루 권장량 (남 / 여)
// 한국인 영양소 섭취기준(2020) 30~49세 값 (권장섭취량, 없으면 충분섭취량), 나이 구분은 아직 안 함
@Getter
@RequiredArgsConstructor
public enum Nutrient {
    VITAMIN_A("비타민 A", "㎍", 800, 650),
    VITAMIN_B1("비타민 B1", "mg", 1.2, 1.1),
    VITAMIN_B2("비타민 B2", "mg", 1.5, 1.2),
    VITAMIN_B6("비타민 B6", "mg", 1.5, 1.4),
    VITAMIN_B12("비타민 B12", "㎍", 2.4, 2.4),
    VITAMIN_C("비타민 C", "mg", 100, 100),
    VITAMIN_D("비타민 D", "㎍", 10, 10),
    VITAMIN_E("비타민 E", "mg", 12, 12),
    FOLATE("엽산", "㎍", 400, 400),
    CALCIUM("칼슘", "mg", 800, 700),
    MAGNESIUM("마그네슘", "mg", 370, 280),
    ZINC("아연", "mg", 10, 8),
    IRON("철", "mg", 10, 14),
    // 오메가3는 EPA+DHA 합계 (한국인 영양소 섭취기준 2020 30~49세 충분섭취량)
    OMEGA3("오메가3", "mg", 400, 260);

    private final String displayName;
    private final String unit;
    private final double maleAmount;
    private final double femaleAmount;

    // 성별에 맞는 권장량 ("F"면 여성, 그 외·모르면 남성 값)
    public double getRecommendationAmount(String gender) {
        if ("F".equals(gender)) {
            return femaleAmount;
        }
        return maleAmount;
    }
}
