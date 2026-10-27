package com.ucamp.app.domain.recommendation.service;

import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;
import com.ucamp.app.domain.recommendation.pipeline.RuleOnlyPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 추천 엔티티 ↔ 저장 형식 ↔ 응답 DTO 변환을 한 곳에 둔다.
 * - applied_rule_ids: 콤마로 이은 문자열, warnings: JSON 배열 문자열. 비어 있으면 null
 * - model_name이 RULE_ONLY면 규칙 기반 폴백 결과 (fallback = true + 안내 문구)
 * - 숫자(용량·현재 섭취량)는 일반 표기로 맞춘다: 계산 직후(1E+2)와 DB에서 읽은 값(100.00)이 같은 100으로 나가도록
 */
@Component
@RequiredArgsConstructor
public class RecommendationMapper {
    private final ObjectMapper objectMapper;

    public RecommendationResponse toResponse(AiRecommendation rec, List<AiRecommendationItem> items) {
        boolean fallback = RuleOnlyPipeline.VERSION.equals(rec.getModelName());
        return new RecommendationResponse(rec.getRecommendationId(), rec.getResponse().getResponseId(),
                rec.getStatus(), rec.getErrorCode(), rec.getModelName(), rec.getSummary(), rec.getCreatedAt(),
                items.stream().filter(i -> !i.isExcluded()).map(this::toItem).toList(),
                items.stream().filter(AiRecommendationItem::isExcluded).map(this::toItem).toList(),
                fallback,
                fallback ? RuleOnlyPipeline.NOTICE : null);
    }

    public String encodeRuleIds(List<String> ruleIds) {
        return ruleIds.isEmpty() ? null : String.join(",", ruleIds);
    }

    public String encodeWarnings(List<String> warnings) {
        return warnings.isEmpty() ? null : objectMapper.writeValueAsString(warnings);
    }

    private RecommendationResponse.Item toItem(AiRecommendationItem item) {
        return new RecommendationResponse.Item(item.getIngredientName(), item.getReason(), item.getSortOrder(),
                plain(item.getRecommendedAmount()), item.getUnit(), plain(item.getCurrentIntake()), item.getIntakeStatus(),
                item.getDoseNote(), decodeWarnings(item.getWarnings()), decodeRuleIds(item.getAppliedRuleIds()));
    }

    // 끝자리 0을 지우고 지수 표기(1E+2)가 되지 않게 소수 자릿수를 0 이상으로 둔다
    private static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static List<String> decodeRuleIds(String value) {
        return value == null ? null : Arrays.asList(value.split(","));
    }

    // JSON 배열이 아니면(직접 넣은 데이터 등) 원문 한 줄로 보여준다
    private List<String> decodeWarnings(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException e) {
            return List.of(json);
        }
    }
}
