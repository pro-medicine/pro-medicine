package com.ucamp.app.domain.product.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 나의 영양관리에서 보여주는 성분과 성인 하루 권장량 (남 / 여)
// 한국인 영양소 섭취기준(2020) 30~49세 값 (권장섭취량, 없으면 충분섭취량), 나이 구분은 아직 안 함
@Getter
@RequiredArgsConstructor
public enum Nutrient {
    // 비타민 A: 한국인 영양소 섭취기준(2020) 성인 상한섭취량은 3,000㎍ RAE인데, 이 상한은 레티놀(기성형 비타민 A)만 기준으로 정한 값임
    // 식약처 성분표의 비타민 A는 레티놀과 베타카로틴을 합친 RAE 총량이라(1㎍ RAE = 레티놀 1㎍ = 보충제 베타카로틴 2㎍) 레티놀 양만 따로 알 수 없음
    // 그래서 상한 판정은 하지 않고(upperAmount 0), 화면에 "상한 미평가"로 따로 알림 (isUpperUnchecked)
    VITAMIN_A("비타민 A", "㎍", 800, 650, 0),
    VITAMIN_B1("비타민 B1", "mg", 1.2, 1.1, 0),
    VITAMIN_B2("비타민 B2", "mg", 1.5, 1.2, 0),
    VITAMIN_B6("비타민 B6", "mg", 1.5, 1.4, 100),
    VITAMIN_B12("비타민 B12", "㎍", 2.4, 2.4, 0),
    VITAMIN_C("비타민 C", "mg", 100, 100, 2000),
    VITAMIN_D("비타민 D", "㎍", 10, 10, 100),
    VITAMIN_E("비타민 E", "mg", 12, 12, 540),
    FOLATE("엽산", "㎍", 400, 400, 1000),
    CALCIUM("칼슘", "mg", 800, 700, 2500),
    MAGNESIUM("마그네슘", "mg", 370, 280, 350),
    ZINC("아연", "mg", 10, 8, 35),
    IRON("철", "mg", 10, 14, 45),
    // 오메가3는 EPA+DHA 합계 (한국인 영양소 섭취기준 2020 30~49세 충분섭취량)
    OMEGA3("오메가3", "mg", 400, 260, 0);

    private final String displayName;
    private final String unit;
    private final double maleAmount;
    private final double femaleAmount;
    private final double upperAmount;

    // 성별에 맞는 권장량 ("F"면 여성, 그 외·모르면 남성 값)
    public double getRecommendationAmount(String gender) {
        if ("F".equals(gender)) {
            return femaleAmount;
        }
        return maleAmount;
    }

    // 상한 기준은 있지만 성분표로는 평가할 수 없는 성분인지 (지금은 비타민 A만)
    // upperAmount가 0인 성분 중 B1처럼 상한 자체가 없는 성분과 구분하려고 따로 둠
    public boolean isUpperUnchecked() {
        return this == VITAMIN_A;
    }
}
