package com.ucamp.app.domain.review.dto;

import java.util.List;

/**
 * 성분 카드의 [제품 추천 보기] 결과 (최대 3개). 약사·채널 이름, 리뷰 원문·요약은 내보내지 않는다.
 * 안전 필터로 제외된 제품은 이름 없이 excludedCount만. products가 비면 message에 안내 문구.
 */
public record ReviewProductResponse(
        String ingredient,
        List<Product> products,
        int excludedCount,
        String notice,
        String message
) {
    public static final String MEMBER_NOTICE =
            "입력하신 약·질환 기준으로 주의 성분이 확인되지 않은 제품이에요. 복용 전 의사·약사와 상담하세요.";
    public static final String GUEST_NOTICE_SUFFIX = " 약·질환 정보가 없어 확인 범위가 좁아요.";
    public static final String EMPTY_MESSAGE = "지금은 추천할 제품이 없어요";

    /**
     * @param alsoContains 같은 추천 결과의 다른 추천 성분 중 이 제품에 같이 든 성분 코드
     * @param cautions     2명 이상 약사가 말한 공통 주의점
     * @param warnings     안전 경고 (찾는 성분 WARN 규칙, 성분 조합 규칙, 확인 못 한 성분)
     */
    public record Product(long catalogProductId, String productName, String brand, String imageUrl,
                          List<String> alsoContains, List<String> cautions, List<String> warnings) {
    }

    public static String notice(boolean guest) {
        return guest ? MEMBER_NOTICE + GUEST_NOTICE_SUFFIX : MEMBER_NOTICE;
    }
}
