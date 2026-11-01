package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.personalized.question.PersonalizedQuestionValidator;
import com.ucamp.app.domain.recommendation.pipeline.BannedWords;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationQualityCheck;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BannedWords 단일 원본의 값·순서가 기존과 동일한지 고정한다.
 */
class BannedWordsTest {

    @Test
    void BASE_목록은_맞춤질문_금지어와_동일() {
        assertThat(BannedWords.BASE).containsExactly("중단", "끊으세요", "진단", "처방", "복용을 멈추");
        assertThat(PersonalizedQuestionValidator.BANNED_WORDS).isSameAs(BannedWords.BASE);
    }

    @Test
    void QUALITY_CHECK_목록은_BASE_플러스_추가_표현() {
        assertThat(BannedWords.QUALITY_CHECK).containsExactly(
                "중단", "끊으세요", "진단", "처방", "복용을 멈추",
                "진료", "의료진", "검사 결과");
    }

    @Test
    void RecommendationQualityCheck_BANNED_WORDS는_QUALITY_CHECK_참조() {
        // package-private이라 직접 접근 불가 → 간접 검증: check()에서 금지어 감지
        var result = RecommendationQualityCheck.check(
                new tools.jackson.databind.ObjectMapper().valueToTree(
                        java.util.Map.of("summary", "검사 결과를 확인하세요",
                                "recommendations", List.of(java.util.Map.of("ingredient", "VITAMIN_C", "reason", "r", "priority", 1)))),
                List.of());
        assertThat(result.summaryErrors()).isNotEmpty();
        assertThat(result.summaryErrors().get(0)).contains("검사 결과");
    }
}
