package com.ucamp.app.domain.recommendation.pipeline;

import com.ucamp.app.domain.health.entity.IngredientCode;
import com.ucamp.app.domain.personalized.question.PersonalizedQuestionValidator;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 추천 출력의 "약한 오류" 검사 (v4.2 프롬프트부터, V4·V6·V7·V8). 형식 오류(RecommendationOutputValidator)와 달리
 * 1회 재요청 뒤에도 남으면 실패로 보지 않는다: 요약 문제는 고정 문구로 바꾸고, 성분 0개는 그대로 저장한다.
 * - 요약: 100자 이내, 2문장 이내, 진료·처방·검사 지시 금지, 제외·금지 성분 언급 금지
 * - 성분: 1개 이상 (안전은 서버 규칙이 검증하므로 불확실하다는 이유로 비우지 않는다)
 */
public final class RecommendationQualityCheck {
    public static final int SUMMARY_MAX_LENGTH = 100;
    public static final int SUMMARY_MAX_SENTENCES = 2;
    /** 요약을 쓸 수 없을 때 대신 쓰는 문구 */
    public static final String FALLBACK_SUMMARY = "입력하신 정보를 바탕으로 고른 성분이에요.";
    /** V2 질문 검증기의 금지어 + 진료·처방·검사 지시 */
    static final List<String> BANNED_WORDS = Stream.concat(PersonalizedQuestionValidator.BANNED_WORDS.stream(),
            Stream.of("진료", "처방", "의료진", "검사 결과")).distinct().toList();
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?。](?=\\s|$)");

    private RecommendationQualityCheck() {
    }

    /** 검사 결과. summaryErrors가 남으면 요약을 고정 문구로 바꾼다 */
    public record Result(List<String> summaryErrors, List<String> itemErrors) {
        public boolean ok() {
            return summaryErrors.isEmpty() && itemErrors.isEmpty();
        }

        public List<String> all() {
            List<String> all = new ArrayList<>(summaryErrors);
            all.addAll(itemErrors);
            return all;
        }
    }

    /** output은 형식 검증을 통과한 것. banned = 이번 사용자의 사전 금지 성분 코드 */
    public static Result check(JsonNode output, Collection<String> banned) {
        List<String> summaryErrors = new ArrayList<>();
        String summary = output.path("summary").asString("");
        int length = summary.codePointCount(0, summary.length());
        if (length > SUMMARY_MAX_LENGTH) {
            summaryErrors.add("summary는 " + SUMMARY_MAX_LENGTH + "자 이내로 쓴다 (지금 " + length + "자)");
        }
        int sentences = sentences(summary);
        if (sentences > SUMMARY_MAX_SENTENCES) {
            summaryErrors.add("summary는 " + SUMMARY_MAX_SENTENCES + "문장 이내로 쓴다 (지금 " + sentences
                    + "문장). 성분별 설명은 각 성분의 reason에 쓴다");
        }
        bannedWord(summary).ifPresent(w -> summaryErrors.add("summary에 진료·처방·검사 지시나 금지 표현(\"" + w
                + "\")을 쓰지 않는다"));
        mentionedBanned(summary, banned).ifPresent(name -> summaryErrors.add("summary에 제외·금지 성분(" + name
                + ")을 언급하지 않는다"));

        List<String> itemErrors = new ArrayList<>();
        if (output.path("recommendations").isEmpty()) {
            itemErrors.add("recommendations에 성분을 1개 이상 고른다. 금기·용량·상한은 서버가 따로 검증하므로 정보가"
                    + " 불확실하다는 이유로 비우지 않는다");
        }
        return new Result(summaryErrors, itemErrors);
    }

    static int sentences(String text) {
        String t = text.strip();
        if (t.isEmpty()) {
            return 0;
        }
        Matcher m = SENTENCE_END.matcher(t);
        int count = 0;
        int lastEnd = 0;
        while (m.find()) {
            count++;
            lastEnd = m.end();
        }
        // 마지막 문장에 마침표가 없어도 한 문장으로 센다
        return t.substring(lastEnd).isBlank() ? count : count + 1;
    }

    private static Optional<String> bannedWord(String text) {
        return BANNED_WORDS.stream().filter(text::contains).findFirst();
    }

    // "비타민 A (레티놀)"은 "비타민A"로도 찾는다 (공백·괄호 설명 무시)
    private static Optional<String> mentionedBanned(String summary, Collection<String> banned) {
        String compact = summary.replaceAll("\\s", "");
        return banned.stream().map(IngredientCode::find).flatMap(Optional::stream)
                .map(IngredientCode::getNameKo)
                .filter(name -> compact.contains(name.replaceAll("\\s*\\(.*\\)", "").replaceAll("\\s", "")))
                .findFirst();
    }
}
