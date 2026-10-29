package com.ucamp.app.domain.health.entity;

import java.util.Arrays;
import java.util.Optional;

// 성분 코드. backend/docs/supplement-recommendation/reference/domain-rules.md 1번 표 그대로
public enum IngredientCode {
    VITAMIN_C("비타민 C", "mg"),
    VITAMIN_A("비타민 A (레티놀)", "mcg RAE"),
    BETA_CAROTENE("베타카로틴", "mg"),
    VITAMIN_B1("비타민 B1 (티아민)", "mg"),
    VITAMIN_B12("비타민 B12", "mcg"),
    VITAMIN_D("비타민 D", "mcg"),
    VITAMIN_K("비타민 K", "mcg"),
    FOLATE("엽산", "mcg DFE"),
    MAGNESIUM("마그네슘", "mg"),
    IRON("철분", "mg"),
    CALCIUM("칼슘", "mg"),
    ZINC("아연", "mg"),
    POTASSIUM("칼륨", "mg"),
    ST_JOHNS_WORT("세인트존스워트", "-"),
    // V2.5 성분 확장 (STEP 16). 유산균은 억 CFU로 저장 (100억 CFU = 100)
    OMEGA3("오메가3 (EPA·DHA)", "mg"),
    PROBIOTICS("프로바이오틱스 (유산균)", "억 CFU"),
    CURCUMIN("커큐민 (강황)", "mg"),
    COQ10("코엔자임 Q10", "mg"),
    COLLAGEN("콜라겐", "mg"),
    GLUCOSAMINE("글루코사민", "mg"),
    NIACIN("나이아신 (비타민 B3)", "mg NE"),
    MILK_THISTLE("밀크씨슬 (실리마린)", "mg"),
    LUTEIN("루테인", "mg"),
    VITAMIN_B6("비타민 B6", "mg");

    private final String nameKo;
    private final String unit;

    IngredientCode(String nameKo, String unit) {
        this.nameKo = nameKo;
        this.unit = unit;
    }

    public String getNameKo() {
        return nameKo;
    }

    public String getUnit() {
        return unit;
    }

    public static Optional<IngredientCode> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(v -> v.name().equals(code)).findFirst();
    }
}
