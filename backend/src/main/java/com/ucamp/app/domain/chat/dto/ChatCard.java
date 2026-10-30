package com.ucamp.app.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ucamp.app.domain.review.dto.ReviewProductResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * 트랙 1 성분 카드. 용량·경고 필드는 V1/V2 추천 카드(RecommendationResponse.Item)와 같은 이름이라 화면이 같은 카드로 그린다.
 * products는 V3 제품 형식 그대로 (버튼을 누르면 추가 호출 없이 펼친다). 약사·채널·리뷰 문장은 넣지 않는다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCard(String ingredient, String reason, int sortOrder, BigDecimal amount, String unit,
                       BigDecimal currentIntake, String intakeStatus, String doseNote, List<String> warnings,
                       List<ReviewProductResponse.Product> products) {
}
